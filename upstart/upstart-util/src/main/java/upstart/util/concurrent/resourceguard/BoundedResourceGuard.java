package upstart.util.concurrent.resourceguard;

import upstart.util.collect.Optionals;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.ShutdownException;
import upstart.util.concurrent.services.AggregateService;
import upstart.util.concurrent.services.ComposableService;
import upstart.util.context.TransientContext;
import upstart.util.exceptions.UncheckedInterruptedException;
import upstart.util.functions.MoreFunctions;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

public interface BoundedResourceGuard extends ComposableService, AutoCloseable {
  boolean tryAcquire(int permits);
  boolean tryAcquire(int permits, Deadline deadline) throws InterruptedException, ShutdownException;
  void acquire(int permits) throws InterruptedException, ShutdownException;
  void release(int permits);

  default boolean tryAcquire() {
    return tryAcquire(1);
  }

  default boolean tryAcquire(Deadline deadline) throws InterruptedException, ShutdownException {
    return tryAcquire(1, deadline);
  }

  default void acquire() throws InterruptedException, ShutdownException {
    acquire(1);
  }

  default void release() {
    release(1);
  }

  default  <O> Promise<O> completeWithResource(Supplier<? extends CompletionStage<O>> job) {
    UncheckedInterruptedException.propagate(this::acquire);
    return releaseAfter(job);
  }

  default  <O> Optional<Promise<O>> tryCompleteWithResource(Supplier<? extends CompletionStage<O>> job) {
    return Optionals.onlyIfFrom(tryAcquire(), () -> releaseAfter(job));
  }

  default <O> Optional<Promise<O>> tryCompleteWithResource(Deadline deadline, Supplier<? extends CompletionStage<O>> job) {
    return UncheckedInterruptedException.getOrPropagate(() -> Optionals.onlyIfFrom(tryAcquire(deadline), () -> releaseAfter(job)));
  }

  default <I, O> Function<I, Promise<O>> asyncFunctionWithResource(
          Function<? super I, ? extends CompletionStage<O>> job
  ) {
    return input -> completeWithResource(MoreFunctions.bind(input, job));
  }


  default TransientContext toTransientContext() {
    return () -> {
      UncheckedInterruptedException.propagate(this::acquire);
      return this::release;
    };
  }

  private <O> Promise<O> releaseAfter(Supplier<? extends CompletionStage<O>> job) {
    return Promise.of(job.get()).uponCompletion(this::release);
  }

  @Override
  default void close() {
    stop();
  }

  default void throwIfShutDown() throws ShutdownException {
    ShutdownException.throwIf(!isRunning());
  }

  default BoundedResourceGuard andThen(BoundedResourceGuard next) {
    return new CompositeResourceGuard(this, next);
  }

  default BoundedResourceGuard started() {
    start().join();
    return this;
  }

  class CompositeResourceGuard extends AggregateService implements BoundedResourceGuard {
    private final BoundedResourceGuard first;
    private final BoundedResourceGuard second;

    public CompositeResourceGuard(BoundedResourceGuard first, BoundedResourceGuard second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean tryAcquire(int permits) {
      if (!first.tryAcquire(permits)) return false;
      boolean acquired = second.tryAcquire(permits);
      if (!acquired) first.release(permits);
      return acquired;
    }

    @Override
    public boolean tryAcquire(int permits, Deadline deadline) throws InterruptedException, ShutdownException {
      if (!first.tryAcquire(permits, deadline)) return false;
      boolean acquired = false;
      try {
        return acquired = second.tryAcquire(permits, deadline);
      } finally {
        if (!acquired) first.release(permits);
      }
    }

    @Override
    public void acquire(int permits) throws InterruptedException, ShutdownException {
      first.acquire(permits);
      try {
        second.acquire(permits);
      } catch (Exception e) {
        first.release(permits);
        throw e;
      }
    }

    @Override
    public void release(int permits) {
      second.release(permits);
      first.release(permits);
    }

    @Override
    protected Iterable<? extends ComposableService> getComponentServices() {
      return Arrays.asList(first, second);
    }
  }
}

