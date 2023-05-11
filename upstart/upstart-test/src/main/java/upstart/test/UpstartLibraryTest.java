package upstart.test;

import com.google.inject.Module;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.config.EnvironmentConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Arranges placeholders for required `application` and `owner` context values, for use
 * when testing libraries or components that are not applications by themselves.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@UpstartTestAnnotation(UpstartLibraryTest.Initializer.class)
@EnvironmentConfig.Fixture(UpstartLibraryTest.PLACEHOLDER_CONTEXT_CONFIG)
public @interface UpstartLibraryTest {
  String PLACEHOLDER_CONTEXT_CONFIG = """
          upstart.context {
            application: <test-placeholder>
            owner: <test-placeholder>
          }
          """;
  Class<? extends Module> value() default Module.class;

  class Initializer implements UpstartTestInitializer {
    @Override
    public void initialize(UpstartTestBuilder testBuilder, ExtensionContext context) {
      UpstartTestInitializer.installAnnotatedModule(UpstartLibraryTest.class, UpstartLibraryTest::value, testBuilder, context);
    }
  }
}
