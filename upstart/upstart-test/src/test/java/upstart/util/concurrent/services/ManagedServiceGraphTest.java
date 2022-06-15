package upstart.util.concurrent.services;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.jupiter.api.Test;
import upstart.UpstartService;
import upstart.config.UpstartModule;
import upstart.managedservices.LifecycleCoordinator;
import upstart.managedservices.ManagedServiceGraph;
import upstart.test.StacklessTestException;
import upstart.test.UpstartExtension;
import upstart.test.systemStreams.CaptureSystemOut;
import upstart.util.concurrent.Deadline;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.awaitility.Awaitility.await;
import static upstart.test.truth.CompletableFutureSubject.assertThat;

class ManagedServiceGraphTest {
  static {
    LogManager.getLogger(ServiceManager.class).setLevel(Level.OFF);
    LogManager.getLogger(LifecycleCoordinator.class.getName() + "." + DelayedStartupService.class.getSimpleName())
            .setLevel(Level.OFF);
  }

  @Test
  void testStartupCancellation() throws Exception {
    DelayedStartupService dependent = new DelayedStartupService();
    DelayedStartupService requirement = new DelayedStartupService();
    ManagedServiceGraph graph = new ManagedServiceGraph(dependent, requirement);
    graph.start();

    assertThat(graph.isRunning()).isFalse();

    graph.stop();

    graph.getStoppedFuture().get(3, TimeUnit.SECONDS);

    assertThat(graph.state()).isEqualTo(Service.State.TERMINATED);
    assertThat(dependent.state()).isEqualTo(Service.State.TERMINATED);
    assertThat(requirement.state()).isEqualTo(Service.State.TERMINATED);
  }

  @Test
  void testProviderDependency() {
    Injector injector = Guice.createInjector(new UpstartModule() {
      @Override
      protected void configure() {
        serviceManager().manage(ProviderService.class)
                .manage(ConsumingService.class);
        bind(ProvidedDependency.class).toProvider(ProviderService.class);
      }
    });

    ServiceDependencyChecker checker = injector.getInstance(ServiceDependencyChecker.class);
    checker.assertThat(ConsumingService.class).dependsUpon(ProviderService.class);
  }

  @Test
  void testProvidedServiceDependency() {
    Injector injector = Guice.createInjector(new UpstartModule() {
      @Override
      protected void configure() {
        serviceManager().manage(ProviderService.class)
                .manage(ConsumingService.class);
        bind(ProvidedDependency.class).toProvider(ProviderService.class);
        bind(ConsumingService.class).toProvider(ConsumingServiceProvider.class).asEagerSingleton();
      }
    });

    ServiceDependencyChecker checker = injector.getInstance(ServiceDependencyChecker.class);
    checker.assertThat(ConsumingService.class).dependsUpon(ProviderService.class);
  }

  @Test
  void testProvidesMethodDependency() {
    Injector injector = Guice.createInjector(new UpstartModule() {
      @Override
      protected void configure() {
        serviceManager().manage(ProviderService.class)
                .manage(ConsumingService.class);
        bind(ProvidedDependency.class).toProvider(ProviderService.class);
      }

      @Provides
      @Singleton
      ConsumingService provideConsumingService(ConsumingServiceProvider provider) {
        return provider.get();
      }
    });

    ServiceDependencyChecker checker = injector.getInstance(ServiceDependencyChecker.class);
    checker.assertThat(ConsumingService.class).dependsUpon(ProviderService.class);
  }

  @Test
  void emptyServiceGraphIsSupported() {
    UpstartExtension.ensureInitialized();
    UpstartService app = UpstartService.builder().build();
    app.startAsync().stopAsync().awaitTerminated();
  }

  @CaptureSystemOut // stifle noisy error-logs... we can't use @SuppressLogs here because of circular dependency
  @Test
  void idleServiceFailureCallsShutdown() throws ExecutionException, InterruptedException, TimeoutException {
    UpstartExtension.ensureInitialized();
    UpstartService app = UpstartService.builder().installModule(new UpstartModule() {
      @Override
      protected void configure() {
        serviceManager().manage(FailingIdleService.class)
                .manage(ProviderService.class);
        externalDependencyBinder().bindExternalDependency(ProviderService.class).dependsUpon(FailingIdleService.class);
      }
    }).build();

    app.start().join();
    FailingIdleService failingService = app.getInstance(FailingIdleService.class);
    var failureException = new StacklessTestException();
    var deadline = Deadline.withinSeconds(10);
    failingService.fail(failureException);
    await().atMost(deadline.remaining()).untilAsserted(() -> assertWithMessage("Expected shutdown to be called")
            .that(failingService.didShutDown).isTrue());
    assertThat(app.getStoppedFuture()).doneWithin(deadline)
            .completedWithExceptionThat().isSameInstanceAs(failureException);
    assertThat(failingService.getTerminationFuture()).doneWithin(deadline)
            .havingResultThat().isEqualTo(Service.State.FAILED);
    assertThat(app.state()).isEqualTo(Service.State.FAILED);
    assertThat(app.getInstance(ProviderService.class).state()).isEqualTo(Service.State.TERMINATED);
  }

  static class DelayedStartupService extends NotifyingService {
    @Override
    protected void onStartupCanceled() {
      doStop();
    }

    @Override
    protected void doStart() {
      // never finishes starting
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }
  }

  @Singleton
  static class FailingIdleService extends IdleService {
    volatile boolean didShutDown = false;

    @Override
    protected void startUp() throws Exception {
    }

    void fail(Throwable cause) {
      notifyFailed(cause);
    }

    @Override
    protected void shutDown() throws Exception {
      didShutDown = true;
    }
  }

  static class ProvidedDependency {

  }

  static class ConsumingServiceProvider implements Provider<ConsumingService> {

    private final ProvidedDependency dep;

    @Inject
    ConsumingServiceProvider(ProvidedDependency dep) {
      this.dep = dep;
    }

    @Override
    public ConsumingService get() {
      return new ConsumingService(dep);
    }
  }

  @Singleton
  static class ProviderService extends NotifyingService implements Provider<ProvidedDependency> {

    private final ProvidedDependency provided = new ProvidedDependency();

    @Inject
    public ProviderService() {
    }

    @Override
    protected void doStart() {
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }

    @Override
    public ProvidedDependency get() {
      return provided;
    }
  }

  @Singleton
  static class ConsumingService extends InitializingService {
    private final ProvidedDependency dependency;

    @Inject
    ConsumingService(ProvidedDependency dependency) {
      this.dependency = dependency;
    }

    @Override
    protected boolean shutDownOnSeparateThread() {
      return false;
    }

    @Override
    protected void startUp() throws Exception {

    }
  }
}
