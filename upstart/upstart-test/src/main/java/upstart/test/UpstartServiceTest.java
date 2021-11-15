package upstart.test;

import com.google.common.util.concurrent.Service;
import upstart.UpstartApplication;
import upstart.services.ManagedServiceGraph;
import upstart.services.ManagedServicesModule;
import upstart.services.ServiceDependencyChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Extends the {@link UpstartTest} features with automated {@link Service#startAsync start} and {@link Service#stopAsync stop}
 * for {@link Service Services} managed by the upstart {@link ManagedServicesModule.ServiceManager}:
 * <p/>
 * Arranges for the {@link ManagedServiceGraph} to be started before, and stopped after, each {@link Test} method. This
 * is a good way to test systems that use upstart's service-lifecycle management.
 * <p/>
 * Note: any Service-failures that occur during ManagedServiceGraph startup or shutdown will result in a failing test.
 * <p/>
 * <h2>{@link AfterServiceStarted @AfterServiceStarted}/{@link BeforeServiceStopped @BeforeServiceStopped} Callbacks</h2>
 * This extension adds additional callbacks to the test-lifecycle: methods on test-classes annotated with
 * {@link AfterServiceStarted} will be invoked after all managed Services have been started (prior to each
 * {@link Test}-method, and methods annotated with {@link BeforeServiceStopped} will be invoked after each test-method,
 * before those Services are stopped.
 * <ol>
 *   <li>{@link BeforeEach}</li>
 *   <li>Injector is built</li>
 *   <li>Test fields/methods annotated with {@link Inject} are populated</li>
 *   <li>{@link AfterInjection}</li>
 *   <li>ManagedServiceGraph is started</li>
 *   <li>{@link AfterServiceStarted}</li>
 *   <li>{@link Test}</li>
 *   <li>{@link BeforeServiceStopped}</li>
 *   <li>ManagedServiceGraph is stopped</li>
 * </ol>
 *
 * @see UpstartTest
 * @see ServiceDependencyChecker
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@UpstartTest
@ExtendWith(UpstartServiceExtension.class)
@Inherited
public @interface UpstartServiceTest {
  int DEFAULT_TIMEOUT = 15;
  TimeUnit DEFAULT_TIMEUNIT = TimeUnit.SECONDS;

  Class<? extends UpstartApplication> value() default UpstartApplication.class;
  int serviceStartupTimeout() default 15;
  int serviceShutdownTimeout() default 15;
  TimeUnit timeoutUnit() default TimeUnit.SECONDS;
}
