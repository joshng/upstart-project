package upstart.test;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.util.collect.PairStream;
import upstart.util.reflect.Reflect;

import java.util.Arrays;
import java.util.Map;

public class AvailablePortConfigExtension implements BeforeEachCallback {
  private static final String CONFIG_ORIGIN_DESCRIPTION = "Test @" + ConfigureAvailablePort.class.getSimpleName();

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    Map<String, Integer> selectedPorts = PairStream.withMappedValues(
                    ExtensionContexts.findTestAnnotations(
                                    ConfigureAvailablePort.class,
                                    Reflect.LineageOrder.SuperclassBeforeSubclass,
                                    extensionContext
                            ).map(ConfigureAvailablePort::value)
                            .flatMap(Arrays::stream)
                            .distinct(),
                    k -> AvailablePortAllocator.allocatePort()
            )
            .toMap();
    UpstartExtension.getRequiredTestBuilder(extensionContext)
            .overrideConfig(ConfigFactory.parseMap(selectedPorts, CONFIG_ORIGIN_DESCRIPTION));
  }
}
