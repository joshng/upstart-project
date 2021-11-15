package upstart.log4j.test;

import upstart.InternalTestBuilder;
import upstart.log.UpstartLogConfig;
import upstart.services.LifecycleCoordinator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Arranges to log the upstart service-graph and its lifecycle transitions; meant to be used only for debugging at
 * development-time (ie, not applied to code that is committed to master)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@SuppressLogs(value = {InternalTestBuilder.class, LifecycleCoordinator.class}, threshold = UpstartLogConfig.LogThreshold.INFO)
public @interface ShowServiceGraph {
}
