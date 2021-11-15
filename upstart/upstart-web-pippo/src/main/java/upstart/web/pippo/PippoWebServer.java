package upstart.web.pippo;

import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import upstart.services.IdleService;
import upstart.services.ServiceLifecycle;
import upstart.web.WebServerConfig;
import ro.pippo.core.Application;
import ro.pippo.core.Pippo;
import ro.pippo.jackson.JacksonJsonEngine;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class PippoWebServer extends IdleService {
  private final Set<PippoWebInitializer> initializers;
  private final Pippo pippo;

  @Inject
  public PippoWebServer(Set<PippoWebInitializer> initializers, Pippo pippo, WebServerConfig config) {
    this.initializers = initializers;
    this.pippo = pippo;
    pippo.getServer().getSettings()
            .host(config.host())
            .port(config.port());
  }

  public int getPort() {
    return pippo.getServer().getPort();
  }

  public Pippo getPippo() {
    return pippo;
  }

  @Override
  protected void startUp() throws Exception {
    setupPippo(pippo, initializers);

    pippo.start();
  }

  @VisibleForTesting
  public static Pippo setupPippo(Pippo pippo, PippoWebInitializer... initializers) {
    setupPippo(pippo, Arrays.asList(initializers));
    return pippo;
  }

  private static void setupPippo(Pippo pippo, Collection<PippoWebInitializer> initializers) {
    Application application = pippo.getApplication();

    JacksonJsonEngine customEngine = new JacksonJsonEngine() {
      @Override
      public void init(Application application) {
        super.init(application);
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new GuavaModule());
        objectMapper.registerModule(new JavaTimeModule());
      }
    };
    customEngine.init(application);
    application.getContentTypeEngines().setContentTypeEngine(customEngine);

    initializers.stream().sorted(Comparator.comparing(PippoWebInitializer::installationPriority))
            .forEach(initializer -> initializer.initializeWeb(application));

    //TODO-TLS: consider enabling this assertion when we intend to use SSL in production
//        if (application.getRuntimeMode() == RuntimeMode.PROD && application.getPippoSettings().getString(PippoConstants.SETTING_SERVER_KEYSTORE_FILE, null) == null) {
//            throw new PippoRuntimeException(
//                    String.format("SSL not properly configured. %s not set.", PippoConstants.SETTING_SERVER_KEYSTORE_FILE));
//        }
  }

  @Override
  protected void shutDown() throws Exception {
    pippo.stop();
  }

}
