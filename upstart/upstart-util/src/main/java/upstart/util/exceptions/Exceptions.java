package upstart.util.exceptions;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import upstart.util.collect.MoreStreams;
import upstart.util.collect.Optionals;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Exceptions {
  private static final Predicate<Throwable> IS_FATAL_ERROR = e -> e instanceof VirtualMachineError
  || e instanceof ThreadDeath
  || e instanceof LinkageError;

  public static boolean isCausedBy(Class<? extends Throwable> causeClass, Throwable t) {
    return causalChain(t).anyMatch(causeClass::isInstance);
  }

  public static <E extends Throwable> Optional<E> findCause(Class<E> causeClass, Throwable t) {
    return MoreStreams.filter(causalChain(t), causeClass).findFirst();
  }

  public static <E extends Throwable> Optional<E> findRootCause(Class<E> causeClass, Throwable t) {
    return MoreStreams.filter(causes(t).stream(), causeClass).findFirst();
  }

  /**
   * @return a {@link Stream} that yields the causal-chain of the given Throwable, starting with the provided Throwable
   * itself and ending with the root cause
   * @see #causes
   */
  public static Stream<Throwable> causalChain(Throwable t) {
    return MoreStreams.generate(t, Throwable::getCause);
  }

  /**
   * @return a {@link List} containing the causal-chain of the given Throwable, starting with the root cause,
   * and ending with the provided Throwable
   *
   * @see #causalChain
   */

  public static ImmutableList<Throwable> causes(Throwable t) {
    return causalChain(t).collect(ImmutableList.toImmutableList()).reverse();
  }

  /**
   * Guava reasonably opted to deprecate {@link Throwables#propagate} ... but we have a uses for the pattern as a
   * one-liner, and know to only use it when appropriate (ie, when we have an untyped Exception/Throwable, and want
   * to throw it unchecked without superfluous wrapping), so we mimic it here.
   */
  public static RuntimeException throwUnchecked(Throwable t) {
    throw throwUncheckedOrWrapped(t, RuntimeException::new);
  }

  public static <E extends Exception> E throwUncheckedOrWrapped(Throwable t, Function<? super Throwable, E> wrapper) throws E {
    Throwables.throwIfUnchecked(t);
    throw wrapper.apply(t);
  }

  public static <T> T callUnchecked(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw throwUnchecked(e);
    }
  }

  // Taken from RxJava throwIfFatal, which was taken from scala
  public static void propagateIfFatal(Throwable t) {
    asFatalError(t).ifPresent(e -> { throw e; });
  }

  public static Optional<Error> asFatalError(Throwable t) {
    return Optionals.asInstance(t, Error.class)
            .filter(IS_FATAL_ERROR);
  }
}
