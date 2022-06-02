package upstart.healthchecks;

import java.util.Map;

public class HealthCheckFailedException extends RuntimeException {
  private final Map<String, HealthCheck.Unhealthy> errors;

  public static void throwIfUnhealthy(Map<String, HealthCheck.Unhealthy> errors) {
    if (!errors.isEmpty()) throw new HealthCheckFailedException(errors);
  }

  public HealthCheckFailedException(Map<String, HealthCheck.Unhealthy> errors) {
    super("Health check failed: " + errors);
    this.errors = errors;
  }

  public Map<String, HealthCheck.Unhealthy> getErrors() {
    return errors;
  }
}
