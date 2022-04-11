package upstart.util.concurrent.services;

public abstract class LightweightService extends IdleService {
  @Override
  protected boolean startUpOnSeparateThread() {
    return false;
  }

  @Override
  protected boolean shutDownOnSeparateThread() {
    return false;
  }
}
