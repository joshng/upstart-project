package upstart.services;

public abstract class InitializingService extends IdleService {
  @Override
  protected void shutDown() {
  }

  @Override
  protected boolean shutDownOnSeparateThread() {
    return false;
  }
}
