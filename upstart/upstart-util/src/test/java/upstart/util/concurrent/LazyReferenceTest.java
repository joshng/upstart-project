package upstart.util.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LazyReferenceTest {
  private final AtomicReference<Object> suppliedRef = new AtomicReference<>();
  private final LazyReference<Object> lazyRef = LazyReference.from(suppliedRef::get);

  @Test
  void nullValueIsRejected() {
    NullPointerException npe = assertThrows(NullPointerException.class, lazyRef::call);
    assertThat(npe).hasMessageThat().isEqualTo("supplyValue must not return null");
  }

  @Test
  void suppliedValueIsMemoized() {

    suppliedRef.set(this);

    assertThat(lazyRef.get()).isSameInstanceAs(this);

    suppliedRef.set(null);

    assertThat(lazyRef.get()).isSameInstanceAs(this);
  }

  @Test
  void getIfPresentReturnsCorrectOptional() {
    assertThat(lazyRef.getIfPresent()).isEmpty();

    suppliedRef.set(this);

    assertThat(lazyRef.getIfPresent()).isEmpty();

    assertThat(lazyRef.get()).isSameInstanceAs(this);

    assertThat(lazyRef.getIfPresent()).hasValue(this);

    assertThat(lazyRef.remove()).hasValue(this);

    assertThat(lazyRef.getIfPresent()).isEmpty();

  }
}