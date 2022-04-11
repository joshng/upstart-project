package upstart;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.ServiceSupervisor;

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
  public void runSupervised() {
    UpstartService.supervise(this);
  }

  public UpstartService.Builder builder() {
    return UpstartService.builder().installModule(this);
  }

  public abstract ServiceSupervisor.BuildFinal configureSupervisor(ServiceSupervisor.ShutdownConfigStage builder);

  @Override
  protected abstract void configure();
}
