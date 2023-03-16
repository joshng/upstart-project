package upstart;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import upstart.config.ObjectMapperFactory;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.provisioning.ProvisionedResource;
import upstart.provisioning.ResourceProvisioningCoordinator;
import upstart.util.concurrent.services.ServiceSupervisor;
import upstart.util.exceptions.UncheckedIO;

import java.util.Comparator;
import java.util.List;

/**
 * A managed upstart Application!

 * <p/>
 * UpstartApplications configure their components by defining {@link UpstartModule} classes, which
 * use Guice {@link AbstractModule} methods as well as upstart-specific features such as
 * configuration-wiring via {@link UpstartModule#bindConfig}, and {@link Service}-lifecycle automation
 * via the {@link UpstartModule#serviceManager()}.
 * <p/>
 * Application <em>main</em>-methods will typically just invoke {@link #runSupervised}.
 */
/*
 *  +------------------------------------------------------+
 *  |   ___ _    _ ___              _                _     |
 *  |  |  _| |  | |_  |            | |              | |    |
 *  |  | | | |  | | | | _ __   ___ | |_  __ _  _ __ | |_   |
 *  |  | | | |  | | | || '_ \ / __|| __|/ _` || '__|| __|  |
 *  |  | | | |__| | | || |_) |\__ \| |_| (_| || |   | |_   |
 *  |  | |_ \____/ _| || .__/ |___/ \__|\__,_||_|    \__|  |
 *  |  |___|      |___||_|                                 |
 *  |                                                      |
 *  +------------------------------------------------------+
 */

public abstract class UpstartApplication extends UpstartModule {
  /**
   * Starts the application, with {@link ServiceSupervisor supervision}, and then waits for it to terminate.
   *
   * @see ServiceSupervisor
   */
  public void runSupervised(String[] args) {
    switch (args.length) {
      case 0 -> {
        buildServiceSupervisor().startAndAwaitTermination();
      }
      case 1 -> {
        switch (args[0]) {
          // todo: make this extensible by registering a set of services that can be run from the command line
          // upstart-cli might even use this facility, instead of the clumsy parent/sub-command approach
          case "help" -> {
            System.out.println("Usage: java -jar <jarfile> [help|provisioned-resources|dump-config]");
            System.exit(0);
          }
          case "provisioned-resources" -> {
            enableToolMode();
            ResourceProvisioningCoordinator provisioningCoordinator = builder().buildInjector()
                    .getInstance(ResourceProvisioningCoordinator.class);

            var comparator = Comparator.<ProvisionedResource.ResourceRequirement, String>
                            comparing(res -> res.resourceType().resourceType())
                    .thenComparing(ProvisionedResource.ResourceRequirement::resourceId);
            List<ProvisionedResource.ResourceRequirement> requirements = provisioningCoordinator.getResources().stream()
                    .map(ProvisionedResource::resourceRequirement)
                    .sorted(comparator)
                    .toList();

            UncheckedIO.runUnchecked(() -> {
              ObjectMapperFactory.buildAmbientObjectMapper()
                      .writerWithDefaultPrettyPrinter()
                      .writeValuesAsArray(System.out)
                      .writeAll(requirements)
                      .close();
            });
            System.exit(0);
          }
          case "dump-config" -> {
            enableToolMode();
            System.out.println(builder().buildInjector().getInstance(UpstartApplicationConfig.class).describeConfig());
            System.exit(0);
          }
          default -> throw new IllegalArgumentException("Unknown argument: " + args[0]);
        }

      }
      default -> throw new IllegalArgumentException("Too many arguments: " + args.length);
    }
  }

  private static void enableToolMode() {
    System.setProperty("UPSTART_OVERRIDES", """
    upstart {
      log { rootLogger: WARN, levels.upstart: WARN }
    }
    """);
  }

  @Override
  protected abstract void configure();

  public abstract ServiceSupervisor.BuildFinal<UpstartService> configureSupervisor(ServiceSupervisor.ShutdownConfigStage<UpstartService> builder);

  public ServiceSupervisor.BuildFinal<UpstartService> buildServiceSupervisor() {
    return configureSupervisor(builder().buildServiceSupervisor());
  }

  public UpstartService.Builder builder() {
    return UpstartService.builder().installModule(this);
  }
}
