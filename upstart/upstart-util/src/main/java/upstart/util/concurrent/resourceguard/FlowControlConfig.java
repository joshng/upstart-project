package upstart.util.concurrent.resourceguard;

import org.immutables.value.Value;

import java.time.Duration;


public interface FlowControlConfig {
  static Builder builder() {
    return new Builder();
  }

  int maxInFlightRequests();

  double maxRequestsPerSec();

  @Value.Default
  default Duration shutdownPollPeriod() {
    return RateLimitedResourceGuard.DEFAULT_SHUTDOWN_POLL_PERIOD;
  }

  default BoundedResourceGuard startResourceGuard() {
    return buildResourceGuard().started();
  }

  private BoundedResourceGuard buildResourceGuard() {
    return new SemaphoreResourceGuard(maxInFlightRequests())
            .andThen(new RateLimitedResourceGuard(maxRequestsPerSec(), shutdownPollPeriod()));
  }

  class Builder extends SimpleFlowControlConfig.Builder {
  }

  @Value.Immutable
  interface SimpleFlowControlConfig extends FlowControlConfig {
    class Builder extends ImmutableSimpleFlowControlConfig.Builder {
    }
  }
}
