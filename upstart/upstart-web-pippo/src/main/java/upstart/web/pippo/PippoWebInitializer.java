package upstart.web.pippo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;
import ro.pippo.core.Application;

/*
 * Initialization and startup management for upstart-web.
 */
public interface PippoWebInitializer {
  void initializeWeb(Application app);

  /**
   * Override this method to influence the order in which initializers are called.
   * Use lower numbers for earlier initialization.
   * @see InitializationPriority#FIRST
   */
  default InitializationPriority installationPriority() {
    return InitializationPriority.DEFAULT;
  }

  @Value.Immutable
  @Tuple
  interface InitializationPriority extends Comparable<InitializationPriority> {
    InitializationPriority DEFAULT = of(0);
    InitializationPriority FIRST = of(Integer.MIN_VALUE);
    InitializationPriority LAST = of(Integer.MAX_VALUE);

    @JsonCreator
    static InitializationPriority of(int value) {
      return ImmutableInitializationPriority.of(value);
    }

    @JsonValue
    int value();

    @Override
    default int compareTo(InitializationPriority o) {
      return Integer.compare(value(), o.value());
    }
  }
}