package utils.module;

import akka.routing.FromConfig;
import akka.routing.RouterConfig;
import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.LoggerUtil;
import play.libs.akka.AkkaGuiceSupport;

public class ActorStartModule extends AbstractModule implements AkkaGuiceSupport {

    LoggerUtil logger = new LoggerUtil(ActorStartModule.class);


    @Override
    protected void configure() {
        logger.info(null, "binding actors for dependency injection");
        final RouterConfig config = new FromConfig();
        for (ACTOR_NAMES actor : ACTOR_NAMES.values()) {
            bindActor(
                    actor.getActorClass(),
                    actor.getActorName(),
                    (props) -> {
                        return props.withRouter(config);
                    });
        }
        logger.info(null, "binding completed");
    }
}
