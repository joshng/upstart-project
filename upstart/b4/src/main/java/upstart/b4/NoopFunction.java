package upstart.b4;

import upstart.util.Nothing;

public class NoopFunction implements UnconfiguredFunction {
  @Override
  public boolean runOnSeparateThread() {
    return false;
  }

  @Override
  public void clean(Nothing config, B4TaskContext context) throws Exception {

  }

  @Override
  public void run(B4TaskContext context) {
  }

  @Override
  public void cancel() {
  }
}
