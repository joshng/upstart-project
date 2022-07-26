package upstart.test;

import com.google.common.util.concurrent.Service;
import upstart.UpstartApplication;
import upstart.UpstartService;
import upstart.config.EnvironmentConfigBuilder;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.HojackConfigProvider;
import upstart.util.collect.MoreStreams;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.services.BaseComposableService;
import upstart.util.concurrent.services.ComposableService;

import java.util.Arrays;
import java.util.List;

public class UpstartApplicationSandbox {
  public static void startAndAwaitTerminated(UpstartApplication app, EnvironmentConfigFixture... configFixtures) {
    startAndAwaitTerminated(app, Arrays.asList(configFixtures));
  }

  // TODO: support fixtures that need to manipulate the environment beyond altering configuration (eg overriding guice-bindings)
  // TODO: adapt this and UpstartExtension/UpstartServiceExtension to share more implementation
  public static void startAndAwaitTerminated(UpstartApplication app, List<? extends EnvironmentConfigFixture> configFixtures) {
    EnvironmentConfigBuilder configBuilder = new EnvironmentConfigBuilder();
    List<ComposableService> fixtureServices = MoreStreams.filter(configFixtures.stream(), Service.class).map(ComposableService::enhance).toList();
    CompletableFutures.allOf(fixtureServices.stream().map(ComposableService::start)).join();

    for (EnvironmentConfigFixture fixture : configFixtures) {
      fixture.applyEnvironmentValues(configBuilder);
    }

    HojackConfigProvider configProvider = configBuilder.buildConfigProvider();
    try {
      app.configureSupervisor(UpstartService.builder(configProvider).installModule(app).buildServiceSupervisor()).startAndAwaitTermination();
    } finally {
      CompletableFutures.allOf(fixtureServices.stream().map(BaseComposableService.STOP)).join();
    }
  }
}
