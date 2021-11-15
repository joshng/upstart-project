package upstart.util.exceptions;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public interface MultiException {

  MultiException with(Throwable toAdd);

  List<Throwable> getThrowables();

  Optional<Throwable> getCombinedThrowable();

  void throwRuntimeIfAny();

   static MultiException collectExceptions(Stream<? extends ThrowingRunnable> runnables) {
     return Empty.consumeAllExceptions(runnables);
  }

  static MultiException collectThrowables(Stream<? extends ThrowingRunnable> runnables) {
    return Empty.consumeAllThrowables(runnables);
  }

  static MultiException closeAll(Stream<? extends AutoCloseable> closeables) {
    return Empty.consumeClosingExceptions(closeables);
  }

  default MultiException consumeException(ThrowingRunnable runnable) {
    try {
      runnable.runOrThrow();
      return this;
    } catch (Exception e) {
      return with(e);
    }
  }

  default MultiException consumeThrowable(ThrowingRunnable runnable) {
    try {
      runnable.runOrThrow();
      return this;
    } catch (Throwable e) {
      return with(e);
    }
  }

  default MultiException consumeAllExceptions(Stream<? extends ThrowingRunnable> runnables) {
     return consumeAllWith(runnables, MultiException::consumeException);
  }

  default MultiException consumeAllThrowables(Stream<? extends ThrowingRunnable> runnables) {
     return consumeAllWith(runnables, MultiException::consumeThrowable);
  }

  default MultiException consumeClosingExceptions(Stream<? extends AutoCloseable> closeables) {
     return consumeAllExceptions(closeables.map(c -> c::close));
  }

  private MultiException consumeAllWith(Stream<? extends ThrowingRunnable> runnables, BiFunction<MultiException, ThrowingRunnable, MultiException> collectWith) {
    return runnables.reduce(
            this,
            collectWith,
            (e1, e2) -> e2.getCombinedThrowable()
                    .map(e1::with)
                    .orElse(e1)
    );
  }

  MultiException Empty = new EmptyImpl();

  class EmptyImpl implements MultiException {
    private EmptyImpl() {
    }

    public MultiException with(Throwable toAdd) {
      if (toAdd instanceof MultiException) return (MultiException) toAdd;
      return new Single(toAdd);
    }

    public List<Throwable> getThrowables() {
      return ImmutableList.of();
    }

    @Override
    public Optional<Throwable> getCombinedThrowable() {
      return Optional.empty();
    }

    public void throwRuntimeIfAny() {
    }
  }

  class Single implements MultiException {
    private final Throwable throwable;

    private Single(Throwable throwable) {
      this.throwable = throwable;
    }

    public MultiException with(Throwable toAdd) {
      return new Multiple().with(throwable).with(toAdd);
    }

    public List<Throwable> getThrowables() {
      return ImmutableList.of(throwable);
    }

    @Override
    public Optional<Throwable> getCombinedThrowable() {
      return Optional.of(throwable);
    }

    public void throwRuntimeIfAny() {
      Throwables.throwIfUnchecked(throwable);
      throw new RuntimeException(throwable);
    }
  }

  class Multiple extends RuntimeException implements MultiException {

    private Multiple() {
    }

    public synchronized MultiException with(Throwable toAdd) {
      if (toAdd instanceof Multiple) {
        for (Throwable throwable : toAdd.getSuppressed()) {
          addSuppressed(throwable);
        }
      } else {
        addSuppressed(toAdd);
      }
      return this;
    }

    public List<Throwable> getThrowables() {
      return Arrays.asList(getSuppressed());
    }

    @Override
    public Optional<Throwable> getCombinedThrowable() {
      return Optional.of(this);
    }

    public void throwRuntimeIfAny() {
      throw this;
    }

    @Override
    public String getMessage() {
      Throwable[] nested = getSuppressed();
      final StringBuilder builder = new StringBuilder("Multiple exceptions thrown (").append(nested.length).append(" total):");
      for (int i = 0; i < nested.length; i++) {
        Throwable throwable = nested[i];
        builder.append("\n\n ----> ").append(i + 1).append(") ")
                .append(throwable.getClass().getName()).append(": ").append(throwable.getMessage());
      }
      return builder.toString();
    }
  }
}
