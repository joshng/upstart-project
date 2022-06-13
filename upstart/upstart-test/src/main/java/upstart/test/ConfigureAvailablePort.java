package upstart.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Locates ports which are available for use by tests, and stores them in the indicated
 * upstart-config locations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(ConfigureAvailablePorts.class)
@ExtendWith(AvailablePortConfigExtension.class)
public @interface ConfigureAvailablePort {
  /** @return the upstart config-paths to hold the selected available ports */
  String[] value();
}
