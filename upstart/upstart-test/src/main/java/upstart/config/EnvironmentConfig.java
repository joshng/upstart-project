package upstart.config;

import upstart.test.UpstartTest;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public @interface EnvironmentConfig {
  /**
   * @see Fixture
   */
  @ExtendWith(EnvironmentConfigExtension.class)
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @interface EnvironmentConfigFixtures {
    Fixture[] value();
  }

  /**
   * Provides a mechanism for applying shared configuration-values to multiple {@link UpstartTest} instances.
   * <p/>
   * Arranges for the listed {@link EnvironmentConfigFixture} implementation(s) and configuration {@link Fixture#resources()}
   * to be applied to each test's {@link EnvironmentConfigBuilder} before the annotated tests are executed.
   * <p/>
   * If multiple fixtures or resources modify the same config-value for a given test, precedence is resolved with
   * "last-write-wins" semantics, as follows:
   * <ol>
   *   <li>annotations that are <em>nearer</em> to the test-method are higher precedence (eg, an annotation on a subclass
   *   is higher precedence than on its superclass, and method-annotations are higher precedence than class-annotations)</li>
   *   <li>{@link EnvironmentConfigFixture} implementations (specified with {@link #value}) are higher precedence than
   *   {@link #resources()} that appear on the same {@link Fixture} annotation</li>
   *   <li>Within the lists of {@link #value() EnvironmentConfigFixtures} and {@link #resources()} on a single annotation,
   *   items that appear later in each list are higher precedence than those that came before them.</li>
   * </ol>
   */
  @ExtendWith(EnvironmentConfigExtension.class)
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Repeatable(EnvironmentConfigFixtures.class)
  @Inherited
  @interface Fixture {
    Class<? extends EnvironmentConfigFixture>[] value() default {};
    String[] resources() default {};
  }

}
