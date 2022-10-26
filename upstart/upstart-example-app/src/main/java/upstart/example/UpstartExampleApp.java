package upstart.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.UpstartApplication;
import upstart.UpstartService;
import upstart.util.concurrent.services.ServiceSupervisor;

import java.time.Duration;


public class UpstartExampleApp extends UpstartApplication {
  private static final Logger LOG = LoggerFactory.getLogger(UpstartExampleApp.class);
  public static final String BANNER = """
           
            +------------------------------------------------------+
            |   ___ _    _ ___              _                _     |
            |  |  _| |  | |_  |            | |              | |    |
            |  | | | |  | | | | _ __   ___ | |_  __ _  _ __ | |_   |
            |  | | | |  | | | || '_ \\ / __|| __|/ _` || '__|| __|  |
            |  | | | |__| | | || |_) |\\__ \\| |_| (_| || |   | |_   |
            |  | |_ \\____/ _| || .__/ |___/ \\__|\\__,_||_|    \\__|  |
            |  |___|      |___||_|                                 |
            |                                                      |
            +------------------------------------------------------+
          """;

  public static void main(String[] args) {
    // UPSTART_ENVIRONMENT must be assigned externally
//        System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, "local-dev");
    new UpstartExampleApp().runSupervised();
  }

  @Override
  public ServiceSupervisor.BuildFinal<UpstartService> configureSupervisor(ServiceSupervisor.ShutdownConfigStage<UpstartService> builder) {
    return builder.shutdownGracePeriod(Duration.ofSeconds(30))
            .logger(LOG)
            .startedLogMessage(BANNER) // optional
            .exitOnUncaughtException(true);
  }

  @Override
  protected void configure() {
    install(CountReportingService.ReportingModule.class);
  }
}
