package org.sunbird.cert.actor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.incredible.UrlManager;
import org.sunbird.incredible.processor.CertificateFactory;
import org.sunbird.incredible.processor.JsonKey;
import org.sunbird.incredible.processor.signature.exceptions.SignatureException;
import org.sunbird.incredible.processor.store.CertStoreFactory;
import org.sunbird.incredible.processor.store.ICertStore;
import org.sunbird.incredible.processor.store.StoreConfig;
import org.sunbird.BaseActor;
import org.sunbird.BaseException;
import org.sunbird.CertsConstant;
import org.sunbird.cloud.storage.exception.StorageServiceException;
import org.sunbird.message.IResponseMessage;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;
import org.sunbird.request.RequestContext;
import org.sunbird.response.Response;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This actor is responsible for certificate verification.
 */
public class CertificateVerifierActor extends BaseActor {

    private ObjectMapper mapper = new ObjectMapper();

    private CertsConstant certsConstant = new CertsConstant();

    @Override
    public void onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        logger.info(request.getRequestContext(), "onReceive method call start for operation {}" ,operation);
        if (JsonKey.VERIFY_CERT.equalsIgnoreCase(operation)) {
            verifyCertificate(request);
        }
    }

    private void verifyCertificate(Request request) throws BaseException {
        Map<String, Object> certificate = new HashMap<>();
        VerificationResponse verificationResponse = new VerificationResponse();
        try {
            if (((Map) request.get(JsonKey.CERTIFICATE)).containsKey(JsonKey.DATA)) {
                certificate = (Map<String, Object>) ((Map) request.get(JsonKey.CERTIFICATE)).get(JsonKey.DATA);
            } else if (((Map) request.get(JsonKey.CERTIFICATE)).containsKey(JsonKey.ID)) {
                certificate = downloadCert(request.getRequestContext(), (String) ((Map<String, Object>) request.get(JsonKey.CERTIFICATE)).get(JsonKey.ID));
            }
            logger.debug(request.getRequestContext(), "Certificate extension {}" ,certificate);
            List<String> certificateType = (List<String>) ((Map) certificate.get(JsonKey.VERIFICATION)).get(JsonKey.TYPE);
            if (JsonKey.HOSTED.equals(certificateType.get(0))) {
                verificationResponse = verifyHostedCertificate(request.getRequestContext(), certificate);
            } else if (JsonKey.SIGNED_BADGE.equals(certificateType.get(0))) {
                verificationResponse = verifySignedCertificate(request.getRequestContext(), certificate);
            }
        } catch (IOException | SignatureException.UnreachableException | SignatureException.VerificationException ex) {
            logger.error(request.getRequestContext(), "verifySignedCertificate:Exception Occurred while verifying certificate. {} " + ex.getMessage(), ex);
            throw new BaseException(IResponseMessage.INTERNAL_ERROR, ex.getMessage(), ResponseCode.SERVER_ERROR.getCode());
        }
        Response response = new Response();
        response.getResult().put("response", verificationResponse);
        sender().tell(response, getSelf());
        logger.info(request.getRequestContext(), "onReceive method call End");
    }

    /**
     * Verifies signed certificate , verify signature value nad expiry date
     *
     * @param certificate certificate object
     * @return
     * @throws SignatureException.UnreachableException
     * @throws SignatureException.VerificationException
     */
    private VerificationResponse verifySignedCertificate(RequestContext requestContext, Map<String, Object> certificate) throws SignatureException.UnreachableException, SignatureException.VerificationException {
        List<String> messages = new ArrayList<>();
        CollectionUtils.addIgnoreNull(messages, verifySignature(certificate));
        CollectionUtils.addIgnoreNull(messages, verifyExpiryDate(requestContext, (String) certificate.get(JsonKey.EXPIRES)));
        return getVerificationResponse(messages);
    }

    /**
     * verifies the hosted certificate
     * verifies expiry date
     *
     * @param certificate certificate object
     * @return
     */
    private VerificationResponse verifyHostedCertificate(RequestContext requestContext, Map<String, Object> certificate) {
        List<String> messages = new ArrayList<>();
        messages.add(verifyExpiryDate(requestContext, (String) certificate.get(JsonKey.EXPIRES)));
        messages.removeAll(Collections.singleton(null));
        return getVerificationResponse(messages);
    }

    private VerificationResponse getVerificationResponse(List<String> messages) {
        VerificationResponse verificationResponse = new VerificationResponse();
        if (messages.size() == 0) {
            verificationResponse.setValid(true);
        } else {
            verificationResponse.setValid(false);
        }
        verificationResponse.setErrorCount(messages.size());
        verificationResponse.setMessages(messages);
        return verificationResponse;

    }


    /**
     * to download certificate from cloud
     *
     * @param url
     * @return
     * @throws IOException
     * @throws BaseException
     */
    private Map<String, Object> downloadCert(RequestContext requestContext, String url) throws IOException, BaseException {
        StoreConfig storeConfig = new StoreConfig(certsConstant.getStorageParamsFromEvn());
        CertStoreFactory certStoreFactory = new CertStoreFactory(null);
        ICertStore certStore = certStoreFactory.getCloudStore(storeConfig);
        certStore.init();
        try {
            String uri = UrlManager.getContainerRelativePath(url);
            String filePath = "conf/";
            certStore.get(uri);
            File file = new File(filePath + getFileName(requestContext, uri));
            Map<String, Object> certificate = mapper.readValue(file, new TypeReference<Map<String, Object>>() {
            });
            file.delete();
            return certificate;
        } catch (StorageServiceException ex) {
            logger.error(requestContext, "downloadCertJson:Exception Occurred while downloading json certificate from the cloud. {} " + ex.getMessage(), ex);
            throw new BaseException("INVALID_PARAM_VALUE", MessageFormat.format(IResponseMessage.INVALID_PARAM_VALUE,
                    url, JsonKey.ID), ResponseCode.CLIENT_ERROR.getCode());
        }
    }

    private String getFileName(RequestContext requestContext, String certId) {
        String idStr = null;
        try {
            URI uri = new URI(certId);
            String path = uri.getPath();
            idStr = path.substring(path.lastIndexOf('/') + 1);
        } catch (URISyntaxException e) {
            logger.debug(requestContext, "getFileName : exception occurred while getting file form the uri {}", e.getMessage());
        }
        return idStr;
    }

    /**
     * verifying certificate signature value
     *
     * @param certificateExtension
     * @return
     * @throws SignatureException.UnreachableException
     * @throws SignatureException.VerificationException
     */
    private String verifySignature(Map<String, Object> certificateExtension) throws SignatureException.UnreachableException, SignatureException.VerificationException {
        String signatureValue = ((Map<String, String>) certificateExtension.get(JsonKey.SIGNATURE)).get(JsonKey.SIGNATURE_VALUE);
        String message = null;
        certificateExtension.remove(JsonKey.SIGNATURE);
        JsonNode jsonNode = mapper.valueToTree(certificateExtension);
        CertificateFactory certificateFactory = new CertificateFactory();
        Boolean isValid = certificateFactory.verifySignature(jsonNode, signatureValue, certsConstant.getEncryptionServiceUrl(),
                ((Map<String, String>) certificateExtension.get(JsonKey.VERIFICATION)).get(JsonKey.CREATOR));
        if (!isValid) {
            message = "ERROR: Assertion.signature - certificate is not valid , signature verification failed";
        }
        return message;
    }

    private String verifyExpiryDate(RequestContext requestContext, String expiryDate) {
        String message = null;
        if (StringUtils.isNotBlank(expiryDate)) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            try {
                Date currentDate = simpleDateFormat.parse(getCurrentDate());
                if (simpleDateFormat.parse(expiryDate).before(currentDate)) {
                    message = "ERROR: Assertion.expires - certificate has been expired";
                }
            } catch (ParseException e) {
                logger.info(requestContext, "verifyExpiryDate : exception occurred parsing date {}" , e.getMessage());
            }
        }
        return message;
    }

    private String getCurrentDate() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }


}