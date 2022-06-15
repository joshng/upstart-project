package upstart.test.truth;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import upstart.util.exceptions.Unchecked;

import java.lang.reflect.Constructor;
import java.util.function.BiFunction;

public class MoreTruth {
  public static <T extends Iterable<?>> IterableSubjectFactory<T> iterables() {
    return new IterableSubjectFactory<>();
  }

  private static class IterableSubjectFactory<T extends Iterable<?>> implements Subject.Factory<IterableSubject, T> {
    public static final BiFunction<FailureMetadata, Object, IterableSubject> ITERABLE_SUBJECT_CONSTRUCTOR;

    static {
      Constructor<IterableSubject> constructor = Unchecked.getUnchecked(() -> IterableSubject.class.getDeclaredConstructor(
              FailureMetadata.class,
              Iterable.class
      ));
      constructor.setAccessible(true);
      ITERABLE_SUBJECT_CONSTRUCTOR = (metadata, actual) -> Unchecked.getUnchecked(() -> constructor.newInstance(metadata, actual));
    }

    @Override
    public IterableSubject createSubject(FailureMetadata metadata, T actual) {
      return ITERABLE_SUBJECT_CONSTRUCTOR.apply(metadata, actual);
    }
  }
}
