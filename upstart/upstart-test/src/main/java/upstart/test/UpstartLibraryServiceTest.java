package upstart.test;

import com.google.inject.Module;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see UpstartServiceTest
 * @see UpstartLibraryTest
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@UpstartLibraryTest
@UpstartServiceTest
@UpstartTestAnnotation(UpstartLibraryServiceTest.Initializer.class)
public @interface UpstartLibraryServiceTest {
  Class<? extends Module> value() default Module.class;

  class Initializer implements UpstartTestInitializer {
    @Override
    public void initialize(UpstartTestBuilder testBuilder, ExtensionContext context) {
      UpstartTestInitializer.installAnnotatedModule(UpstartLibraryServiceTest.class, UpstartLibraryServiceTest::value, testBuilder, context);
    }
  }
}
