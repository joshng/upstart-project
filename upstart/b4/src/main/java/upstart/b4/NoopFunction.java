package upstart.b4;

import upstart.util.Nothing;

public class NoopFunction implements UnconfiguredFunction {
  @Override
  public boolean runOnSeparateThread() {
    return false;
  }

  @Override
  public void clean(Nothing config, B4TargetContext context) throws Exception {

  }

  @Override
  public void run(B4TargetContext context) {
  }

  @Override
  public void cancel() {
  }
}
