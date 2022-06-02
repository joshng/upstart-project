package upstart.healthchecks;

import com.google.common.base.Throwables;
import org.immutables.value.Value;
import upstart.util.annotations.Tuple;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.lenientFormat;

public interface HealthCheck {
  CompletableFuture<HealthStatus> checkHealth();

  default HealthStatus healthy() {
    return HealthStatus.HEALTHY;
  }

  sealed interface HealthStatus {
    HealthStatus HEALTHY = Healthy.INSTANCE;

    static HealthStatus healthy() {
      return HEALTHY;
    }

    static HealthStatus healthyIf(boolean condition, String errorMessage) {
      return condition ? HEALTHY : unhealthy(errorMessage);
    }

    static HealthStatus healthyIf(boolean condition, String lenientFormat, Object... args) {
      return condition ? HEALTHY : unhealthy(lenientFormat(lenientFormat, args));
    }

    static HealthStatus healthyIf(boolean condition, Supplier<String> errorMessage) {
      return condition ? HEALTHY : unhealthy(errorMessage.get());
    }

    static HealthStatus unhealthy(String message) {
      return Unhealthy.message(message);
    }

    static HealthStatus unhealthy(Throwable message) {
      return Unhealthy.message(message);
    }

    boolean unhealthy();

  }

  enum Healthy implements HealthStatus {
    INSTANCE;

    @Override
    public boolean unhealthy() {
      return false;
    }
  }

  @Tuple
  non-sealed interface Unhealthy extends HealthStatus {
    static Unhealthy message(String message) {
      return ImmutableUnhealthy.of(message);
    }

    static Unhealthy message(Throwable error) {
      return ImmutableUnhealthy.of(Throwables.getStackTraceAsString(error));
    }

    @Override
    default boolean unhealthy() {
      return true;
    }

    String message();

    @Value.Check
    default void check() {
      checkArgument(!message().isEmpty(), "[empty message]");
    }
  }
}
