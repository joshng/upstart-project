package upstart.cli;

import upstart.UpstartService;
import upstart.util.exceptions.ThrowingRunnable;

public abstract class ExecutableSubCommand<P extends UpstartCommand> extends UpstartSubCommand<P> implements ThrowingRunnable {
  @Override
  public void runOrThrow() throws Exception {
    UpstartService upstartService = startService();
    upstartService.injector().injectMembers(this);
    execute(upstartService);
  }

  protected abstract void execute(UpstartService upstartService) throws Exception;
}
