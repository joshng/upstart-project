package upstart.util.concurrent;

import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.exceptions.ThrowingUnaryOperator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Similar to an "Actor" (https://en.wikipedia.org/wiki/Actor_model):
 * Provides thread-safe non-reentrant (ie, sequential) access to some underlying state (of type {@link T}), protected by
 * a "Future chain": each interaction with the enclosed state is staged as a callback which is invoked upon completion
 * of the previous interaction.<p/>
 * <p>
 * Unlike typical Actors, however, this implementation allows interactions to be completed asynchronously:
 * the fundamental operation {@link #chain} and the utility {@link #visitAsync} accept transformations
 * that return a {@link CompletionStage CompletionStage}; subsequent interactions will be attached to the completion
 * of the returned Future.<p/>
 * <p>
 * This thing also supports invoking wrapper-behavior before and after each interaction: the {@link FutureCell}
 * may be created with {@link Builder#preProcess} and/or {@link Builder#postProcess} interceptors that may observe
 * or transform the invocations and results of each interaction. This can be used to handle errors {@link Builder#onError}
 * in a uniform manner, or modify enqueued behaviors based on external factors (eg, during shutdown).
 */
public class FutureCell<T> {
  private final AtomicReference<CompletableFuture<T>> next = new AtomicReference<>();
  private final ThrowingUnaryOperator<ThrowingUnaryOperator<CompletableFuture<T>>> preProcessor;
  private final ThrowingUnaryOperator<CompletableFuture<T>> postProcessor;
  private final Executor executor;

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }


  /**
   * @see #builder
   */
  public FutureCell(
          CompletableFuture<T> initialValue,
          ThrowingUnaryOperator<ThrowingUnaryOperator<CompletableFuture<T>>> preProcessor,
          ThrowingUnaryOperator<CompletableFuture<T>> postProcessor,
          Executor executor
  ) {
    this.executor = executor;
    next.set(initialValue);
    this.preProcessor = preProcessor;
    this.postProcessor = postProcessor;
  }

  /**
   * Enqueue the specified action to be performed on the underlying state after all prior actions
   * are completed. The action will be enhanced by the configured preProcessor prior to being
   * invoked, and the stored state-reference may subsequently be influenced by the postProcessor.
   *
   * @param updater action to perform on the existing (pre-processor enhanced) state
   * @return result of the (post-processor intercepted) update
   */
  public CompletableFuture<T> chain(ThrowingUnaryOperator<CompletableFuture<T>> updater) {
    return Promise.thatCompletes(promise -> promise.completeWith(
            postProcessor.applyOrThrow( // let the result be observed
                    preProcessor.applyOrThrow(updater) // let the action be enhanced
                            .applyOrThrow( // apply the enhanced action
                                    next.getAndSet(promise) // to the previous result, replacing it with this outcome
                            )
            ))
    );
  }

  /**
   * Enqueue the specified action to be performed on the underlying state after all prior actions
   * are completed. The action will be enhanced by the configured preProcessor prior to being
   * invoked, and the stored state-reference may subsequently be influenced by the postProcessor.
   *
   * @param block that operates on the (pre-processor enhanced) state
   * @return A future that will complete after block has executed
   */
  public CompletableFuture<?> run(Consumer<? super T> block) {
    return chain(current -> current.thenApplyAsync(value -> {
      block.accept(value);
      return value;
    }, executor));
  }

  /**
   * Enqueue the specified action to be performed on the underlying state after all prior actions
   * are completed. The action will be enhanced by the configured preProcessor prior to being
   * invoked, and the stored state-reference may subsequently be influenced by the postProcessor.
   * <p>
   * The result of the visitor does not affect the underlying state.
   *
   * @param visitor that operates on the (pre-processor enhanced) state
   * @return A future that will hold the result of the visitor
   */
  public <V> CompletableFuture<V> visit(Function<? super T, ? extends V> visitor) {
    return Promise.thatCompletes(promise -> run(instance -> {
      promise.tryComplete(() -> visitor.apply(instance));
    }));
  }

  /**
   * Enqueue the specified action to be performed on the underlying state after all prior actions
   * are completed. The action will be enhanced by the configured preProcessor prior to being
   * invoked, and the stored state-reference may subsequently be influenced by the postProcessor.
   * <p>
   * The result of the visitor does not affect the underlying state.
   * <p>
   * Like {@link #visit(Function)}, but for (async) functions that return a CompletionStage.
   *
   * @param visitor that operates on the (pre-processor enhanced) state
   * @return A future that will hold the result of the visitor
   */
  public <V> CompletableFuture<V> visitAsync(Function<? super T, ? extends CompletionStage<V>> visitor) {
    return Promise.thatCompletes(promise -> chain(
            current ->
                    promise.completeWith(
                            current.thenComposeAsync(visitor, executor)
                    ).thenCompose(__ -> current)
            )
    );
  }

  public boolean isAlive() {
    return !next.get().isCompletedExceptionally();
  }

  public static class Builder<T> {
    private ThrowingUnaryOperator<ThrowingUnaryOperator<CompletableFuture<T>>> preProcessor = ThrowingUnaryOperator.identity();
    private ThrowingUnaryOperator<CompletableFuture<T>> postProcessor = ThrowingUnaryOperator.identity();
    private Executor executor = MoreExecutors.directExecutor();

    public Builder<T> preProcess(ThrowingUnaryOperator<ThrowingUnaryOperator<CompletableFuture<T>>> preprocessor) {
      this.preProcessor = preprocessor;
      return this;
    }

    public Builder<T> postProcess(ThrowingUnaryOperator<CompletableFuture<T>> postprocessor) {
      this.postProcessor = postprocessor;
      return this;
    }

    public Builder<T> onError(Consumer<Throwable> errorHandler) {
      return postProcess(f -> f.whenComplete((__, e) -> {
        if (e != null) errorHandler.accept(e);
      }));
    }

    public Builder<T> executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public FutureCell<T> build(T initialValue) {
      return build(CompletableFuture.completedFuture(initialValue));
    }

    public FutureCell<T> build(CompletableFuture<T> initialValue) {
      return new FutureCell<>(initialValue, preProcessor, postProcessor, executor);
    }
  }
}
