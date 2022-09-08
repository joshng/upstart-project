package upstart.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.typesafe.config.ConfigParseOptions;
import io.upstartproject.hojack.HojackConfigMapper;
import upstart.test.ExtensionContexts;
import upstart.test.SingletonParameterResolver;
import upstart.util.annotations.Identifier;
import upstart.util.collect.Optionals;
import upstart.util.reflect.Reflect;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.immutables.value.Value;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.stream.Stream;

public class EnvironmentConfigExtension extends SingletonParameterResolver<EnvironmentConfigBuilder> {
  public EnvironmentConfigExtension() {
    super(EnvironmentConfigBuilder.class);
  }

  @Override
  protected EnvironmentConfigBuilder createContext(ExtensionContext extensionContext) {
    // TODO: allow configMapper to be customized
//    EnvironmentConfigBuilder configBuilder = new EnvironmentConfigBuilder("TEST", new HojackConfigMapper());
    EnvironmentConfigBuilder configBuilder = new EnvironmentConfigBuilder("TEST", new HojackConfigMapper(ObjectMapperFactory.buildAmbientObjectMapper()));

    ExtensionContexts.findRepeatableTestAnnotations(
            EnvironmentConfig.Fixture.class,
            Reflect.LineageOrder.SuperclassBeforeSubclass,
            extensionContext
    ).flatMap(EnvironmentConfigExtension::fixtures)
            .collect(ImmutableList.toImmutableList())
            .reverse() // arrange for distinct() to retain the LAST instance of each duplicated fixture in the ordered stream
            .stream().distinct()
            .collect(ImmutableList.toImmutableList())
            .reverse() // reverse again to process in correct top-down/left-to-right order
            .forEach(fixture -> fixture.applyEnvironmentValues(configBuilder));

    return configBuilder;
  }

  public static EnvironmentConfigBuilder getConfigBuilder(ExtensionContext context) {
    return getOrCreateContextFrom(EnvironmentConfigExtension.class, context);
  }

  private static Stream<EnvironmentConfigFixture> fixtures(EnvironmentConfig.Fixture fixtureAnnotation) {
    return Streams.concat(
            Stream.of(fixtureAnnotation.resources()).map(ResourceFixture::of),
            Stream.of(fixtureAnnotation.impl()).map(DelegatingFixture::of),
            Optionals.onlyIf(!fixtureAnnotation.value().isEmpty(), fixtureAnnotation.value())
                    .map(HoconStringFixture::of)
                    .stream()
    );
  }

  @Value.Immutable(intern = true)
  @Identifier
  abstract static class DelegatingFixture implements EnvironmentConfigFixture {
    static DelegatingFixture of(Class<? extends EnvironmentConfigFixture> fixtureClass) {
      return ImmutableDelegatingFixture.of(fixtureClass);
    }

    abstract Class<? extends EnvironmentConfigFixture> fixtureClass();

    @Override
    public void applyEnvironmentValues(TestConfigBuilder<?> config) {
      // create a new instance each time, to ensure state isn't accidentally retained across runs
      Reflect.newInstance(fixtureClass()).applyEnvironmentValues(config);
    }
  }

  @Value.Immutable(intern = true)
  @Identifier
  abstract static class ResourceFixture implements EnvironmentConfigFixture {
    static ResourceFixture of(String resource) {
      return ImmutableResourceFixture.of(resource);
    }

    abstract String resourceName();

    @Value.Lazy
    Config parsedConfig() {
      return ConfigFactory.parseResourcesAnySyntax(resourceName());
    }

    @Override
    public void applyEnvironmentValues(TestConfigBuilder<?> config) {
      config.overrideConfig(parsedConfig());
    }
  }

  @Value.Immutable(intern = true)
  @Identifier
  abstract static class HoconStringFixture implements EnvironmentConfigFixture {
    private static final ConfigParseOptions FIXTURE_ORIGIN_DESCRIPTION = ConfigParseOptions.defaults().setOriginDescription(
            EnvironmentConfig.Fixture.class.getCanonicalName().substring(EnvironmentConfig.class.getPackageName().length() + 1));

    static HoconStringFixture of(String config) {
      return ImmutableHoconStringFixture.of(config);
    }

    abstract String config();

    @Value.Lazy
    Config parsedConfig() {
      return ConfigFactory.parseString(config(), FIXTURE_ORIGIN_DESCRIPTION);
    }

    @Override
    public void applyEnvironmentValues(TestConfigBuilder<?> config) {
      config.overrideConfig(parsedConfig());
    }
  }
}
