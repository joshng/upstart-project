package upstart.b4;

import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import upstart.b4.functions.MavenConfig;
import upstart.b4.incremental.Glob;
import upstart.commandExecutor.ProcBuilderCommandExecutor;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.ThreadPoolService;
import upstart.util.concurrent.NamedThreadFactory;
import upstart.util.Nothing;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class B4Module extends UpstartModule {
  private final B4Application app;

  public B4Module(B4Application app) {
    this.app = app;
  }

  @Provides
  TargetInvocationGraph provideInvocationGraph() {
    return app.getInvocationGraph();
  }

  @Override
  public void configure() {
    install(new ProcBuilderCommandExecutor.CommandExecutorModule());
    bindConfig(MavenConfig.class);
    bind(Nothing.class).toInstance(Nothing.NOTHING);
    install(new FactoryModuleBuilder().build(Glob.Factory.class));
    bind(B4Application.class).toInstance(app);
    serviceManager().manage(B4GraphDriver.class).manage(ThreadPool.class);
  }

  @Singleton
  static class ThreadPool extends ThreadPoolService {

    protected ThreadPool() {
      super(Duration.ofSeconds(2));
    }

    @Override
    protected ExecutorService buildExecutorService() {
      return Executors.newCachedThreadPool(new NamedThreadFactory("b4"));
    }
  }
}
