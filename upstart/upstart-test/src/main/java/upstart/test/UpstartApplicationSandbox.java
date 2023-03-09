package upstart.test;

import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import org.immutables.value.Value;
import upstart.UpstartApplication;
import upstart.UpstartService;
import upstart.config.EnvironmentConfigBuilder;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.HojackConfigProvider;
import upstart.provisioning.ProvisionedResource;
import upstart.util.collect.MoreStreams;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.services.ComposableService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

@Value.Immutable
public interface UpstartApplicationSandbox {
  static ImmutableUpstartApplicationSandbox.Builder builder(UpstartApplication application) {
    return new Builder().application(application);
  }

  UpstartApplication application();

  Set<EnvironmentConfigFixture> configFixtures();

  Set<Module> modules();

  static void startAndAwaitTerminated(UpstartApplication app, EnvironmentConfigFixture... configFixtures) {
    startAndAwaitTerminated(app, Arrays.asList(configFixtures));
  }

  default void startAndAwaitTerminated() {
    EnvironmentConfigBuilder configBuilder = new EnvironmentConfigBuilder();
    List<ComposableService> fixtureServices = MoreStreams.filter(configFixtures().stream(), Service.class)
            .map(ComposableService::enhance)
            .toList();
    CompletableFutures.allOf(fixtureServices.stream().map(ComposableService::start)).join();

    for (EnvironmentConfigFixture fixture : configFixtures()) {
      fixture.applyEnvironmentValues(configBuilder);
    }

    HojackConfigProvider configProvider = configBuilder.buildConfigProvider();
    try {
      UpstartService.Builder builder = UpstartService.builder(configProvider);
      for (EnvironmentConfigFixture fixture : configFixtures()) {
        if (fixture instanceof Initializer initializer) initializer.initializeSandbox(builder);
      }
      application().configureSupervisor(builder
              .installModule(application())
              .installModule(Modules.combine(modules()))
              .buildServiceSupervisor()
      ).startAndAwaitTermination();
    } finally {
      CompletableFutures.allOf(fixtureServices.stream()
              .map(ComposableService.STOP_QUIETLY)
              .map(CompletionStage::toCompletableFuture)).join();
    }
  }

  interface Initializer {
    void initializeSandbox(UpstartService.Builder builder);
  }

  class Builder extends ImmutableUpstartApplicationSandbox.Builder {
    public Builder installModule(Module module) {
      return addModules(module);
    }

    public Builder provisionResources(ProvisionedResource.ResourceType resourceType, ProvisionedResource.ResourceType... resourceTypes) {
      return addModules(binder -> ProvisionedResource.provisionAtStartup(binder, resourceType, resourceTypes));
    }
  }

  // TODO: support fixtures that need to manipulate the environment beyond altering configuration (eg overriding guice-bindings)
  // TODO: adapt this and UpstartExtension/UpstartServiceExtension to share more implementation
  static void startAndAwaitTerminated(UpstartApplication app, List<? extends EnvironmentConfigFixture> configFixtures) {
    builder(app).configFixtures(configFixtures).build().startAndAwaitTerminated();
  }
}
