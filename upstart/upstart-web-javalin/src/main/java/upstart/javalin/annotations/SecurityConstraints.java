package upstart.javalin.annotations;

import com.google.common.collect.ImmutableSet;
import io.javalin.core.security.RouteRole;
import io.javalin.plugin.openapi.annotations.OpenApiSecurity;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
public interface SecurityConstraints {
  SecurityConstraints NONE = new SecurityConstraints() {
    @Override
    public Set<RouteRole> requiredRoles() {
      return ImmutableSet.of();
    }

    @Override
    public Set<OpenApiSecurity> security() {
      return ImmutableSet.of();
    }

    @Override
    public SecurityConstraints merge(SecurityConstraints other) {
      return other;
    }
  };

  static ImmutableSecurityConstraints.Builder builder() {
    return ImmutableSecurityConstraints.builder();
  }

  Set<RouteRole> requiredRoles();
  Set<OpenApiSecurity> security();

  default boolean isEmpty() {
    return requiredRoles().isEmpty() && security().isEmpty();
  }

  default SecurityConstraints merge(SecurityConstraints other) {
    return other.isEmpty() ? this : builder().from(this).from(other).build();
  }

  @Value.Lazy
  default RouteRole[] roleArray() {
    return requiredRoles().toArray(new RouteRole[0]);
  }

  @Value.Lazy
  default OpenApiSecurity[] securityArray() {
    return security().toArray(new OpenApiSecurity[0]);
  }
}
