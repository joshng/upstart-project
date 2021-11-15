package upstart.b4;

import java.util.stream.Stream;

public interface B4TargetGenerator<C> {
  Stream<TargetSpec> generateTargets(C config);
}
