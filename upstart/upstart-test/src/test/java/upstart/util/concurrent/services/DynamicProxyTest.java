package upstart.util.concurrent.services;

import com.google.inject.Provides;
import upstart.config.UpstartModule;
import upstart.proxy.Lazy;
import upstart.proxy.LazyProvider;
import upstart.test.UpstartLibraryTest;
import upstart.test.UpstartServiceTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

@UpstartLibraryTest
@UpstartServiceTest
public class DynamicProxyTest extends UpstartModule {
  @Inject DependentService dependentService;
  @Inject ServiceDependencyChecker dependencyChecker;

  @Override
  protected void configure() {
    bindDynamicProxy(UnderlyingService.ProxiedDep.class).initializedFrom(UnderlyingService.class, UnderlyingService::getProvidedDep, UnderlyingService.class);
    bindLazyProviderProxy(UnderlyingService.ProxiedDep.class);
    serviceManager().manage(UnderlyingService.class).manage(DependentService.class);
  }

  @Provides @LazyProvider UnderlyingService.ProxiedDep provideExplicitValue(UnderlyingService service) {
    return service.getProvidedDep();
  }

  @Test
  void ok() {
    dependentService.checkProxyInvocation();
    dependencyChecker.assertThat(dependentService).dependsUpon(UnderlyingService.class);
  }

  @Singleton
  static class DependentService extends NotifyingService {
    private final UnderlyingService.ProxiedDep proxy;
    private final UnderlyingService.ProxiedDep explicitProxy;

    @Inject
    DependentService(@Lazy UnderlyingService.ProxiedDep lazyProxy, UnderlyingService.ProxiedDep explicitProxy) {
      this.proxy = lazyProxy;
      this.explicitProxy = explicitProxy;
    }

    @Override
    protected void doStart() {
      checkProxyInvocation();
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }

    void checkProxyInvocation() {
      assertThat(proxy.checkRealInstance()).isEqualTo(7);
      assertThat(explicitProxy.checkRealInstance()).isEqualTo(7);
    }
  }

  @Singleton
  static class UnderlyingService extends NotifyingService {
    private final int value = 7;

    public class ProxiedDep {
      int checkRealInstance() {
        checkState(isRunning());
        return value;
      }
    }

    ProxiedDep getProvidedDep() {
      checkState(isRunning(), "Service was not running");
      return new ProxiedDep();
    }

    @Override
    protected void doStart() {
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }
  }
}
