package upstart.util.concurrent.resourceguard;

import org.immutables.value.Value;


public interface FlowControlConfig extends RateLimitedResourceGuard.RateLimitConfig {
  static Builder builder() {
    return new Builder();
  }

  int maxInFlightRequests();

  default CompositeResourceGuard<SemaphoreResourceGuard, RateLimitedResourceGuard> startResourceGuard() {
    return buildFlowControlGuard().started();
  }

  private CompositeResourceGuard<SemaphoreResourceGuard, RateLimitedResourceGuard> buildFlowControlGuard() {
    return new SemaphoreResourceGuard(maxInFlightRequests())
            .andThen(buildRateLimitGuard());
  }

  class Builder extends SimpleFlowControlConfig.Builder {
  }

  @Value.Immutable
  interface SimpleFlowControlConfig extends FlowControlConfig {
    class Builder extends ImmutableSimpleFlowControlConfig.Builder {
    }
  }
}
