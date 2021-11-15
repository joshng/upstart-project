package upstart.test;

import com.google.inject.Injector;
import com.google.inject.Module;
import upstart.UpstartApplication;
import upstart.services.UpstartService;
import upstart.config.EnvironmentConfigExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Arranges for an {@link Injector} to be configured by {@link UpstartService.Builder#buildInjector} before
 * each test-case. Test-objects that implement {@link Module} will be automatically installed into the Injector.
 * <p/>
 * <h2>Overriding configurations and bindings</h2>
 * Tests using this extension have access to a {@link UpstartTestBuilder} that may be used in {@link BeforeEach}
 * methods to customize the upstart context, including:
 * <ul>
 *   <li>{@link UpstartTestBuilder#overrideConfig} to fine-tune the provided HOCON configs</li>
 *   <li>{@link UpstartTestBuilder#overrideBindings} to alter the guice-bindings defined by the installed modules
 *   (eg, to inject mock instances instead of the real types bound by the production modules)</li>
 * </ul>
 *
 * To interact with the {@link UpstartTestBuilder}, declare it as a parameter on a {@link BeforeEach} method.
 *
 * Example:
 * <pre>{@code
 * class MyTest extends UpstartModule {
 *   // set up a mock for use below; could also use @Mock on the field with the MockitoExtension
 *   private MyComponent mockComponent = Mockito.mock(MyComponent.class);
 *
 *   protected void configure() {
 *     install(new MyApplicationModule()); // set up the test application, same as in production
 *   }
 *
 *   @BeforeEach
 *   void setupUpstart(UpstartTestBuilder testBuilder) {
 *
 *     // override HOCON values
 *     testBuilder.overrideConfig("my.config.value", 17);
 *
 *     //install a mock instance
 *     testBuilder.overrideBindings(new UpstartModule() {
 *       public void configure() {
 *         bind(MyComponent.class).toInstance(mockComponent);
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>@Injecting managed components</h2>
 * Tests may annotate fields and methods with {@link Inject @Inject} to obtain access to components which are managed by
 * the upstart {@link Injector}. These fields are populated after all {@link BeforeEach} methods, and before each
 * {@link Test} method is invoked.
 * <p/>
 *
 * <h2>{@link AfterInjection @AfterInjection} Callback</h2>
 * This extension adds an additional callback to the test-lifecycle: methods on test-classes annotated with
 * {@link AfterInjection} will be invoked <em>after</em> the {@link Injector} has been built and all test fields/methods
 * annotated with {@link Inject} have been populated, but <em>before</em> each {@link Test} method is invoked:
 * <ol>
 *   <li>{@link BeforeEach}</li>
 *   <li>Injector is built</li>
 *   <li>Test fields/methods annotated with {@link Inject} are populated</li>
 *   <li>{@link AfterInjection}</li>
 *   <li>{@link Test}</li>
 * </ol>
 * <p/>
 * <h2>Testing Managed Services
 * See {@link UpstartServiceTest} to automate starting and stopping of Managed Services in tests.
 *
 * @see UpstartServiceTest
 * @see UpstartExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith({EnvironmentConfigExtension.class, UpstartExtension.class})
@Inherited
public @interface UpstartTest {
  Class<? extends Module> value() default Module.class;
}
