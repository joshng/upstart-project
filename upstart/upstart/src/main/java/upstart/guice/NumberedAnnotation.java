package upstart.guice;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.util.concurrent.atomic.AtomicInteger;

public class NumberedAnnotation {
  private final AtomicInteger counter = new AtomicInteger();

  public <T> Key<T> nextKey(Class<T> keyType) {
    return nextKey(TypeLiteral.get(keyType));
  }

  public <T> Key<T> nextKey(TypeLiteral<T> keyType) {
    return Key.get(keyType, next());
  }

  public Numbered next() {
    return ImmutableNumbered.of(counter.incrementAndGet());
  }
}
