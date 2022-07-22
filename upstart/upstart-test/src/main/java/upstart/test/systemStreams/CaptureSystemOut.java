package upstart.test.systemStreams;

import org.junit.jupiter.api.extension.ExtendWith;
import upstart.test.SetupPhase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Arranges to capture bytes written to System.out for inspection by tests.
 *
 * Captured bytes may be retrieved via {@link SystemOutCaptor#getCapturedBytes()}, after accepting the {@link SystemOutCaptor}
 * as a test-method parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(SystemOutCaptureExtension.class)
public @interface CaptureSystemOut {
  /**
   * The phase of the test where the capture should start. If this is {@link SetupPhase#None}, capture will not
   * be started automatically; in that case, you must call {@link SystemOutCaptor#startCapture()} at the appropriate
   * time yourself.
   */
  SetupPhase value() default SetupPhase.BeforeTestExecution;
}
