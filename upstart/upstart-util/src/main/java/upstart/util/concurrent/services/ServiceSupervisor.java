package upstart.util.concurrent.services;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.util.concurrent.Threads;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

/**
 * A utility wrapper for a guava {@link Service} that binds the process lifecycle to the Service lifecycle.<p/>
 * <p>
 * Features:
 * <ul>
 *  <li>invokes {@link System#exit} to kill the process if the supervised service {@link Service#failureCause fails}</li>
 *  <li>installs a {@link Runtime#addShutdownHook(Thread) shutdown hook} to allow the supervised service an opportunity to
 *  shutdown gracefully (with a configurable {@link ShutdownConfigStage#shutdownGracePeriod shutdownGracePeriod})
 *  if the process is killed via a signal (eg, SIGINT/SIGTERM)</li>
 *  <li>installs an {@link Thread#setDefaultUncaughtExceptionHandler UncaughtExceptionHandler} to log
 *  (and optionally {@link System#exit exit}, via {@link ServiceSupervisor.BuildFinal#exitOnUncaughtException exitOnUncaughtException})
 *  any uncaught exception.
 *  </li>
 * </ul>
 * <p>
 * This is intended to be used from the "main" method for a process. Note that the {@link #startAndAwaitTermination} method <em>does not return!</em>
 * Sample usage:
 * <pre>{@code
 * ServiceSupervisor
 *   .shutdownGracePeriod(Duration.ofSeconds(10))
 *   .startService(() -> buildMyService()); // does not return
 * }</pre>
 */
@Value.Immutable
@Value.Style(overshadowImplementation = true)
public abstract class ServiceSupervisor<S extends ComposableService> {

  private static final FutureTask<?> TERMINATOR = new FutureTask<>(ServiceSupervisor::terminateSystem);
  private static final Executor TERMINATOR_THREAD = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
          .setNameFormat(threadName("Exit"))
          .build());
  private static final AtomicBoolean SINGLETON_INSTALLED = new AtomicBoolean(false);

  public static <S extends ComposableService> ShutdownConfigStage<S> forService(Callable<? extends S> service) {
    return new Builder<S>().serviceBuilder(service);
  }

  public abstract Callable<? extends S> serviceBuilder();

  public abstract Duration shutdownGracePeriod();

  @Value.Default
  public Duration pendingTransitionLogInterval() {
    return Duration.ofSeconds(10);
  }

  @Value.Default
  public Logger logger() {
    return LoggerFactory.getLogger(ServiceSupervisor.class);
  }

  @Value.Default
  public boolean exitOnUncaughtException() {
    return true;
  }

  @Value.Default
  public String startingLogMessage() {
    return "Starting service...";
  }

  @Value.Default
  public String startedLogMessage() {
    return "Service started";
  }

  @Value.Derived
  @Value.Auxiliary
  public S supervisedService() {
    checkState(!SINGLETON_INSTALLED.getAndSet(true), "Only one ServiceSupervisor can be configured per JVM process");

    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      logger().error("UNCAUGHT EXCEPTION in thread {}", t, e);
      if (exitOnUncaughtException()) terminateSystemAsync();
    });

    S supervisedService;
    try {
      supervisedService = serviceBuilder().call();
    } catch (Exception e) {
      logger().error("Failed to build service, terminating", e);
      throw terminateSystem();
    }

    supervisedService.addListener(new Service.Listener() {
                                    @Override
                                    public void stopping(Service.State from) {
                                      logger().info("Service stopping... (was {})", from);
                                    }

                                    @Override
                                    public void failed(Service.State from, Throwable failure) {
                                      logger().error("Service failed while {}, terminating:\n{}",
                                              from,
                                              supervisedService,
                                              supervisedService.failureCause());
                                      terminateSystemAsync();
                                    }
                                  }, MoreExecutors.directExecutor()
    );

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      boolean halt = true;
      try {
        if (supervisedService.isStoppable()) logger().info("ShutdownHook invoked, stopping services...");
        Instant deadline = Instant.now().plus(shutdownGracePeriod());
        halt = !awaitOperation("shutdown", now -> Duration.between(now, deadline), supervisedService::stop);
        if (!halt) {
          logger().info("Shutdown complete! Bye now :)");
        } else {
          logger().warn("Graceful shutdown timed out after {}, halting forcibly! Thread dump:\n{}",
                  shutdownGracePeriod(),
                  Threads.formatThreadDump());
        }
      } catch (Throwable e) {
        logger().error("Unexpected exception during shutdown, halting!\n{}", supervisedService, e);
      } finally {
        if (halt) Runtime.getRuntime().halt(1);
      }
    }, threadName("ShutdownHook")));

    return supervisedService;
  }

  public void startAndAwaitTermination() {
    start();
    awaitTermination();
  }

  public ServiceSupervisor<S> start() {
    try {
      ComposableService supervisedService = supervisedService();
      logger().info(startingLogMessage());

      awaitOperation("startup", now -> pendingTransitionLogInterval(), supervisedService::start);
      logger().info(startedLogMessage());
    } catch (Throwable e) {
      logger().error("Failed to start, terminating", e);
      throw terminateSystem();
    }
    return this;
  }

  private boolean awaitOperation(String desc, Function<Instant, Duration> waitTime, Supplier<CompletableFuture<?>> operation) throws InterruptedException, ExecutionException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    CompletableFuture<?> future = operation.get();

    Duration sleep;
    while (!(sleep = waitTime.apply(Instant.now())).isNegative()) {
      try {
        future.get(Math.min(sleep.toMillis(), pendingTransitionLogInterval().toMillis()), TimeUnit.MILLISECONDS);
        logger().info("({} took {})", desc, stopwatch);
        return true;
      } catch (TimeoutException e) {
        logger().warn("... still awaiting {} after {}: {}", desc, stopwatch, supervisedService());
      }
    }
    return false;
  }

  public void awaitTermination() {
    try {
      terminationFuture().join();
      logger().info("Service stopped.");
    } catch (Throwable e) {
      logger().error("Service failed to stop cleanly, terminating", e);
      throw terminateSystem();
    }
  }

  public CompletableFuture<Service.State> terminationFuture() {
    return supervisedService().getStoppedFuture();
  }

  private static String threadName(String threadPurpose) {
    return ServiceSupervisor.class.getSimpleName() + "-" + threadPurpose;
  }

  public static void terminateSystemAsync() {
    TERMINATOR_THREAD.execute(TERMINATOR);
  }

  private static AssertionError terminateSystem() {
    System.exit(1);
    return new AssertionError("Unpossible");
  }

  public interface ShutdownConfigStage<S extends ComposableService> {
    /**
     * Initializes the value for the {@link ServiceSupervisor#shutdownGracePeriod() shutdownGracePeriod} attribute.
     * @param shutdownGracePeriod The value for shutdownGracePeriod
     * @return {@code this} builder for use in a chained invocation
     */
    BuildFinal<S> shutdownGracePeriod(Duration shutdownGracePeriod);
  }

  public interface BuildFinal<S extends ComposableService> {

    /**
     * Initializes the value for the {@link ServiceSupervisor#pendingTransitionLogInterval() pendingTransitionLogInterval} attribute.
     * <p><em>If not set, this attribute will have a default value as returned by the initializer of {@link ServiceSupervisor#pendingTransitionLogInterval() pendingTransitionLogInterval}.</em>
     * @param pendingTransitionLogInterval The value for pendingTransitionLogInterval
     * @return {@code this} builder for use in a chained invocation
     */
    BuildFinal<S> pendingTransitionLogInterval(Duration pendingTransitionLogInterval);

    /**
     * Initializes the value for the {@link ServiceSupervisor#logger() logger} attribute.
     * <p><em>If not set, this attribute will have a default value as returned by the initializer of {@link ServiceSupervisor#logger() logger}.</em>
     * @param logger The value for logger
     * @return {@code this} builder for use in a chained invocation
     */
    BuildFinal<S> logger(Logger logger);

    /**
     * Initializes the value for the {@link ServiceSupervisor#exitOnUncaughtException() exitOnUncaughtException} attribute.
     * <p><em>If not set, this attribute will have a default value as returned by the initializer of {@link ServiceSupervisor#exitOnUncaughtException() exitOnUncaughtException}.</em>
     * @param exitOnUncaughtException The value for exitOnUncaughtException
     * @return {@code this} builder for use in a chained invocation
     */
    BuildFinal<S> exitOnUncaughtException(boolean exitOnUncaughtException);

    /**
     * Initializes the value for the {@link ServiceSupervisor#startingLogMessage() startingLogMessage} attribute.
     * <p><em>If not set, this attribute will have a default value as returned by the initializer of {@link ServiceSupervisor#startingLogMessage() startingLogMessage}.</em>
     * @param startingLogMessage The value for startingLogMessage
     * @return {@code this} builder for use in a chained invocation
     */
    BuildFinal<S> startingLogMessage(String startingLogMessage);

    /**
     * Initializes the value for the {@link ServiceSupervisor#startedLogMessage() startedLogMessage} attribute.
     * <p><em>If not set, this attribute will have a default value as returned by the initializer of {@link ServiceSupervisor#startedLogMessage() startedLogMessage}.</em>
     * @param startedLogMessage The value for startedLogMessage
     * @return {@code this} builder for use in a chained invocation
     */
    BuildFinal<S> startedLogMessage(String startedLogMessage);

    ServiceSupervisor<S> build();

    ServiceSupervisor<S> start();

    void startAndAwaitTermination();
  }

  static class Builder<S extends ComposableService> extends ImmutableServiceSupervisor.Builder<S> implements ShutdownConfigStage<S>, BuildFinal<S> {
    public ServiceSupervisor<S> start() {
      ServiceSupervisor<S> supervisor = build();
      supervisor.start();
      return supervisor;
    }

    @Override
    public void startAndAwaitTermination() {
      build().startAndAwaitTermination();
    }
  }
}
