package upstart.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.NotifyingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.google.common.truth.Truth.assertThat;

@UpstartLibraryTest
@UpstartServiceTest
class UpstartServiceExtensionTest extends UpstartModule {

  @Inject FakeService service;

  @Override
  protected void configure() {
    serviceManager().manage(FakeService.class);
  }

  @Test
  void testServicesExtension() {
    assertThat(service.isRunning()).isTrue();
  }

  @AfterEach
  void checkShutdown() {
    assertThat(service.isRunning()).isFalse();
  }

  @Singleton
  static class FakeService extends NotifyingService {
    @Override
    protected void doStart() {
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }
  }

  @Nested
  class WithServiceDisabled {
    @BeforeEach
    void disable(UpstartTestBuilder builder) {
      builder.disableServiceManagement(FakeService.class);
    }

    @Test
    void disabledServiceNotRunning() {
      assertThat(service.isRunning()).isFalse();
    }
  }
}
