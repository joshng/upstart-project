package upstart.util;

import com.google.common.truth.Truth8;
import upstart.util.exceptions.MultiException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiExceptionTest {
  @Test
  void successfulTasksYieldNoException() {
    AtomicInteger value = new AtomicInteger(0);
    MultiException result = MultiException.collectExceptions(Stream.of(
            value::incrementAndGet,
            value::incrementAndGet
    ));

    Truth8.assertThat(result.getCombinedThrowable()).isEmpty();
    assertThat(result.getThrowables()).isEmpty();
    result.throwRuntimeIfAny();
    assertWithMessage("tasks should complete").that(value.get()).isEqualTo(2);
  }

  @Test
  void singleExceptionIsPreserved() {
    AtomicInteger value = new AtomicInteger(0);
    IllegalStateException e1 = new IllegalStateException("Exception 1");
    MultiException result = MultiException.collectExceptions(Stream.of(
            () -> { throw e1; },
            value::incrementAndGet
    ));

    Truth8.assertThat(result.getCombinedThrowable()).isPresent();
    assertThat(result.getThrowables()).containsExactly(e1);
    RuntimeException e = assertThrows(IllegalStateException.class, result::throwRuntimeIfAny);
    assertThat(e).isSameInstanceAs(e1);
    assertWithMessage("successful tasks should complete").that(value.get()).isEqualTo(1);
  }

  @Test
  void multipleExceptionsAreCollected() {
    AtomicInteger value = new AtomicInteger(0);
    IllegalStateException e1 = new IllegalStateException("Exception 1");
    IllegalArgumentException e2 = new IllegalArgumentException("Exception 2");
    MultiException result = MultiException.collectExceptions(Stream.of(
            () -> { throw e1; },
            value::incrementAndGet,
            () -> { throw e2; },
            value::incrementAndGet
    ));

    Truth8.assertThat(result.getCombinedThrowable()).isPresent();
    assertThat(result.getThrowables()).containsExactly(e1, e2);
    RuntimeException e = assertThrows(RuntimeException.class, result::throwRuntimeIfAny);
    assertThat(e).hasMessageThat().contains("Multiple exceptions thrown");
    assertThat(e).hasMessageThat().contains("Exception 1");
    assertThat(e).hasMessageThat().contains("Exception 2");
    assertWithMessage("successful tasks should complete").that(value.get()).isEqualTo(2);
  }
}