package org.sunbird;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.incredible.processor.JsonKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class QRStorageParams {

    private LoggerUtil logger = new LoggerUtil(this.getClass());
    public Map<String, Object> storeParams;

    public QRStorageParams(String storageType) {
        storeParams = getStorageParamsFromEnv(storageType);
    }

    private Map<String, Object> getStorageParamsFromEnv(String type) {
        logger.info(null, "QRStorageParams getting storage params from env ");
        Map<String, Object> storeParams = new HashMap<>();
        storeParams.put(JsonKey.TYPE, type);
        if (StringUtils.isNotBlank(type)) {
            if (type.equals(JsonKey.AZURE)) {
                storeParams.put(JsonKey.AZURE, getAzureParams());
            }
            if (type.equals(JsonKey.AWS)) {
                storeParams.put(JsonKey.AWS, getAwsParams());
            }
        }
        return storeParams;
    }

    private Map<String, String> getAzureParams() {
        Map<String, String> azureParams = new HashMap<>();
        azureParams.put(JsonKey.containerName, System.getenv(JsonKey.PUBLIC_CONTAINER_NAME));
        azureParams.put(JsonKey.ACCOUNT, System.getenv(JsonKey.PUBLIC_AZURE_STORAGE_KEY));
        azureParams.put(JsonKey.KEY, System.getenv(JsonKey.PUBLIC_AZURE_STORAGE_SECRET));
        return azureParams;
    }

    private Map<String, String> getAwsParams() {
        Map<String, String> awsParams = new HashMap<>();
        awsParams.put(JsonKey.containerName, System.getenv(JsonKey.PUBLIC_CONTAINER_NAME));
        awsParams.put(JsonKey.ACCOUNT, System.getenv(JsonKey.PUBLIC_AWS_STORAGE_KEY));
        awsParams.put(JsonKey.KEY, System.getenv(JsonKey.PUBLIC_AWS_STORAGE_SECRET));
        return awsParams;
    }
}
