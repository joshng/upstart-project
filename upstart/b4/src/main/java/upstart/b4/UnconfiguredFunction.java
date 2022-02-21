package upstart.b4;

import upstart.util.Nothing;

public interface UnconfiguredFunction extends B4Function<Nothing> {
  @Override
  default void run(Nothing config, B4TaskContext context) throws Exception {
    run(context);
  }

  void run(B4TaskContext context) throws Exception;
}
