package upstart.guice;

import java.util.concurrent.atomic.AtomicInteger;

public class NumberedAnnotation {
  private final AtomicInteger counter = new AtomicInteger();

  public Numbered next() {
    return ImmutableNumbered.of(counter.incrementAndGet());
  }
}
