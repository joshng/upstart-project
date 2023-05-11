package upstart.test;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.util.collect.PairStream;
import upstart.util.reflect.Reflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Map;

/**
 * Locates ports which are available for use by tests, and stores them in the indicated
 * upstart-config locations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(ConfigureAvailablePorts.class)
@UpstartTestAnnotation(ConfigureAvailablePort.Initializer.class)
public @interface ConfigureAvailablePort {
  /** @return the upstart config-paths to hold the selected available ports */
  String[] value();

  class Initializer implements UpstartTestInitializer {
    private static final String CONFIG_ORIGIN_DESCRIPTION = "Test @" + ConfigureAvailablePort.class.getSimpleName();

    @Override
    public void initialize(UpstartTestBuilder testBuilder, ExtensionContext extensionContext) {
      Map<String, Integer> selectedPorts = PairStream.withMappedValues(
                      ExtensionContexts.findRepeatableTestAnnotations(
                                      ConfigureAvailablePort.class,
                                      Reflect.LineageOrder.SuperclassBeforeSubclass,
                                      extensionContext
                              ).map(ConfigureAvailablePort::value)
                              .flatMap(Arrays::stream)
                              .distinct(),
                      k -> AvailablePortAllocator.allocatePort()
              )
              .toMap();
      testBuilder.overrideConfig(ConfigFactory.parseMap(selectedPorts, CONFIG_ORIGIN_DESCRIPTION));
    }
  }
}
