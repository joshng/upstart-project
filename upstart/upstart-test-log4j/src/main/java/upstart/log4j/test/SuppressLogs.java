package upstart.log4j.test;

import upstart.log.UpstartLogConfig;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies log-thresholds for specific tests (methods or classes), without impacting other tests in the same process.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(SuppressedLogs.class)
@Inherited
@ExtendWith(LogExtension.class)
public @interface SuppressLogs {
  Class<?>[] value() default {};
  String[] categories() default {};

  UpstartLogConfig.LogThreshold threshold() default UpstartLogConfig.LogThreshold.FATAL;
}
