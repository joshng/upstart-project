package upstart.test.truth;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import java.util.Optional;

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

public class ExtendedOptionalSubject<T> extends Subject {
  private final Optional<T> actual;

  ExtendedOptionalSubject(
          FailureMetadata failureMetadata,
          Optional<T> subject
  ) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  // TODO(cpovirk): Consider making OptionalIntSubject and OptionalLongSubject delegate to this.

  public static <T> ExtendedOptionalSubject<T> assertThat(Optional<T> optional) {
    return assertAbout(ExtendedOptionalSubject.<T>optionals()).that(optional);
  }

  /**
   * Fails if the {@link Optional}{@code <T>} is empty or the subject is null.
   */
  public void isPresent() {
    if (actual == null) {
      failWithActual(simpleFact("expected present optional"));
    } else if (!actual.isPresent()) {
      failWithoutActual(simpleFact("expected to be present"));
    }
  }

  public T get() {
    isPresent();
    return actual.orElseThrow();
  }

  /**
   * Fails if the {@link Optional}{@code <T>} is present or the subject is null.
   */
  public void isEmpty() {
    if (actual == null) {
      failWithActual(simpleFact("expected empty optional"));
    } else if (actual.isPresent()) {
      failWithoutActual(
              simpleFact("expected to be empty"), fact("but was present with value", actual.get()));
    }
  }

  /**
   * Fails if the {@link Optional}{@code <T>} does not have the given value or the subject is null.
   *
   * <p>To make more complex assertions on the optional's value split your assertion in two:
   *
   * <pre>{@code
   * assertThat(myOptional).isPresent();
   * assertThat(myOptional.get()).contains("foo");
   * }</pre>
   */
  public void hasValue(Object expected) {
    if (expected == null) {
      throw new NullPointerException("Optional cannot have a null value.");
    }
    if (actual == null) {
      failWithActual("expected an optional with value", expected);
    } else if (!actual.isPresent()) {
      failWithoutActual(fact("expected to have value", expected), simpleFact("but was empty"));
    } else {
      check("get()").that(actual.get()).isEqualTo(expected);
    }
  }

  public <S extends Subject> S hasValueThat(Factory<S, ? super T> factory) {
    isPresent();
    return check("get()").about(factory).that(actual.get());
  }

  public static <T> Factory<ExtendedOptionalSubject<T>, Optional<T>> optionals() {
    return (metadata, subject) -> new ExtendedOptionalSubject<>(metadata, subject);
  }
}
