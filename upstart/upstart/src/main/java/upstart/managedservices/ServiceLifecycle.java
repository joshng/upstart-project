package upstart.managedservices;

import com.google.common.util.concurrent.Service;
import com.google.inject.BindingAnnotation;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * May be used to annotate a {@link Service} class to identify the phase of startup/shutdown when the service
 * must be {@link Service#startAsync started}/{@link Service#stopAsync stopped}. Services annotated with
 * {@link Phase#Infrastructure} will be started before, and stopped after, Services assigned to {@link Phase#Application}
 * (which is the default).
 * <p/>
 * Primarily intended to distinguish <em>infrastructure</em> Services (eg, responsible for emitting telemetry or
 * operational functionality) from <em>business</em> Services, to permit the infrastructure to span the entire
 * lifecycle of the business.
 * <p/>
 * In particular, components associated with Infrastructure Services may {@link ManagedServicesModule#bindServiceListener
 * bind Service.Listeners}
 * to observe the lifecycle events for Application Services.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Value.Immutable(intern = true)
@Tuple
@Inherited
@BindingAnnotation
public @interface ServiceLifecycle {
  Phase value() default Phase.Application;

  /**
   * @see ServiceLifecycle
   */
  enum Phase {
    /**
     * {@link Service Services} {@link ManagedServicesModule.ServiceManager#manage managed} in the
     * {@link #Infrastructure} phase are started before, and stopped after, those in the {@link #Application} phase.
     */
    Infrastructure,
    /**
     * {@link Service Services} {@link ManagedServicesModule.ServiceManager#manage managed} in the
     * {@link #Application} phase are started after, and stopped before, those in the {@link #Infrastructure} phase.
     */
    Application;

    private ServiceLifecycle annotation;

    public ServiceLifecycle annotation() {
      if (annotation == null) annotation = ImmutableServiceLifecycle.of(this);
      return annotation;
    }
  }
}
