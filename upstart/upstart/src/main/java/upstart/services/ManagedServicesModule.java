package upstart.services;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartModule;
import upstart.guice.GuiceDependencyGraph;
import upstart.util.annotations.Identifier;
import org.immutables.value.Value;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

@SuppressWarnings("UnstableApiUsage")
public class ManagedServicesModule extends UpstartModule {
  private static final ServiceLifecycle INFRA_LIFECYCLE = ServiceLifecycle.Phase.Infrastructure.annotation();
  public static final Key<ManagedServiceGraph> INFRASTRUCTURE_GRAPH_KEY = Key.get(ManagedServiceGraph.class, INFRA_LIFECYCLE);
  public static final Key<ManagedServiceGraph> APP_GRAPH_KEY = Key.get(ManagedServiceGraph.class, ServiceLifecycle.Phase.Application.annotation());
  private static final ManagedServicesModule INFRA_MODULE = new ManagedServicesModule(INFRA_LIFECYCLE);
  private static final TypeLiteral<Set<KeyRef>> KEYREF_SET_TYPE = new TypeLiteral<Set<KeyRef>>() {};
  private static final TypeLiteral<Set<Service>> SERVICE_SET_TYPE = new TypeLiteral<Set<Service>>() {};
  private static final TypeLiteral<Set<Service.Listener>> LISTENER_SET_TYPE = new TypeLiteral<Set<Service.Listener>>() {};

  private final ServiceLifecycle lifecycle;

  private ManagedServicesModule(ServiceLifecycle lifecycle) {
    this.lifecycle = lifecycle;
  }

  public static void init(Binder binder) {
    binder.install(INFRA_MODULE);
  }

  @Override
  public void configure() {
    Key<ManagedServiceGraph> annotatedGraphKey = Key.get(ManagedServiceGraph.class, lifecycle);
    install(new PrivateModule() {
      @Override
      protected void configure() {
        bind(KEYREF_SET_TYPE).to(Key.get(KEYREF_SET_TYPE, lifecycle));
        bind(LISTENER_SET_TYPE).to(Key.get(LISTENER_SET_TYPE, lifecycle));
        bind(SERVICE_SET_TYPE).to(Key.get(SERVICE_SET_TYPE, lifecycle));
        bind(annotatedGraphKey).toProvider(ManagedServiceGraphProvider.class).asEagerSingleton();
        expose(annotatedGraphKey);
      }
    });

    switch (lifecycle.value()) {
      case Infrastructure:
        install(new GuiceDependencyGraph.GuiceModule());
        ScheduledService.executorServiceBinder(binder());
        break;
      case Application:
        bind(ManagedServiceGraph.class).to(annotatedGraphKey).in(Scopes.SINGLETON);
        // FALL-THROUGH //
      default:
        // install infrastructure here, in case there are no infrastructure services
        install(INFRA_MODULE);
        new ServiceManager(binder()).manage(annotatedGraphKey, ServiceLifecycle.Phase.Infrastructure);
        break;
    }

    // ensure multibinders are defined even if no instances are added
    keyBinder(binder(), lifecycle);
    serviceBinder(binder(), lifecycle);
    serviceListenerBinder(binder(), lifecycle);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (! (o instanceof ManagedServicesModule)) return false;

    ManagedServicesModule that = (ManagedServicesModule) o;

    return lifecycle.value() == that.lifecycle.value();
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + lifecycle.value().hashCode();
  }

  public static ServiceManager serviceManager(Binder binder) {
    return new ServiceManager(binder);
  }

  /**
   * Starts a binding to register a {@link Service.Listener} to observe the lifecycle events of
   * {@link ServiceLifecycle.Phase#Application Application} services.
   * <p/>
   * Note that to avoid lifecycle problems, any services required by the registered listeners should usually be managed
   * in {@link ServiceLifecycle.Phase#Infrastructure}.
   *
   * @see ServiceLifecycle
   */
  public static LinkedBindingBuilder<Service.Listener> bindServiceListener(Binder binder) {
    return bindServiceListener(binder, ServiceLifecycle.Phase.Application);
  }

  public static LinkedBindingBuilder<Service.Listener> bindServiceListener(Binder binder, ServiceLifecycle.Phase phase) {
    return serviceListenerBinder(binder, phase.annotation()).addBinding();
  }

  /**
   * Services managed by this facility will be {@link Service#startAsync started} and {@link Service#stopAsync stopped}
   * in the correct order to ensure that no Service is {@link Service.State#RUNNING RUNNING} when any other Service
   * injected into it as a guice <em>dependency</em> via {@link Inject @Inject} (directly or transitively) is
   * <b>NOT</b> RUNNING.
   *
   * @see IdleService
   * @see ExecutionThreadService
   * @see InitializingService
   * @see ScheduledService
   * @see NotifyingService
   * @see UpstartService#supervise
   */
  public static class ServiceManager {
    private final Binder binder;
    private final Map<ServiceLifecycle.Phase, ServiceBinder> phaseBinders = new EnumMap<>(ServiceLifecycle.Phase.class);

    private ServiceManager(Binder binder) {
      this.binder = binder;
    }

    public ServiceManager manage(Class<? extends Service> implementation) {
      return manage(Key.get(implementation));
    }

    public ServiceManager manage(Key<? extends Service> targetKey) {
      Optional<ServiceLifecycle> annotation = Optional.ofNullable(
              targetKey.getTypeLiteral().getRawType().getAnnotation(ServiceLifecycle.class)
      );
      ServiceLifecycle.Phase phase = annotation
              .map(ServiceLifecycle::value)
              .orElse(ServiceLifecycle.Phase.Application);
      return manage(targetKey, phase);
    }

    public ServiceManager manage(Key<? extends Service> targetKey, ServiceLifecycle.Phase phase) {
      phaseBinders.computeIfAbsent(phase, ServiceBinder::new).add(targetKey);
//      binder.bind(targetKey).in(Scopes.SINGLETON); // this causes problems for instance-bindings
      return this;
    }

    private class ServiceBinder {
      private final Multibinder<KeyRef> keyRefBinder;
      private final Multibinder<Service> serviceBinder;

      ServiceBinder(ServiceLifecycle.Phase phase) {
        ServiceLifecycle lifecycle = phase.annotation();
        binder.install(new ManagedServicesModule(lifecycle));
        this.keyRefBinder = keyBinder(binder, lifecycle);
        this.serviceBinder = serviceBinder(binder, lifecycle);
      }

      void add(Key<? extends Service> serviceKey) {
        serviceBinder.addBinding().to(serviceKey);
        keyRefBinder.addBinding().toInstance(KeyRef.of(serviceKey));
        binder.getProvider(serviceKey);
      }
    }
  }

  static class ManagedServiceGraphProvider implements Provider<ManagedServiceGraph> {
    private final GuiceDependencyGraph dependencyGraph;

    private final Set<KeyRef> managedServiceKeyRefs;
    private final Set<Service> services;
    private final Set<Service.Listener> serviceListeners;

    @Inject
    ManagedServiceGraphProvider(
            GuiceDependencyGraph dependencyGraph,
            Set<KeyRef> managedServiceKeyRefs,
            Set<Service> services,
            Set<Service.Listener> serviceListeners
    ) {
      this.dependencyGraph = dependencyGraph;
      this.managedServiceKeyRefs = managedServiceKeyRefs;
      this.services = services;
      this.serviceListeners = serviceListeners;
      checkState(services.size() == managedServiceKeyRefs.size(), "BUG: mismatch between services and keys", managedServiceKeyRefs, services);
    }

    @Override
    public ManagedServiceGraph get() {
      Collection<Key<? extends Service>> serviceKeys = managedServiceKeyRefs.stream()
              .map(KeyRef::serviceKey)
              .collect(Collectors.toList());

      List<Key<?>> nonSingletonKeys = serviceKeys.stream()
              .filter(serviceKey -> !dependencyGraph.isSingleton(serviceKey))
              .collect(Collectors.toList());

      checkState(nonSingletonKeys.isEmpty(),
              "Managed services must be bound as Singleton (annotate with @Singleton, or bind in(Scopes.SINGLETON)): %s",
              nonSingletonKeys
      );

      Multimap<Service, Service> graph = dependencyGraph.computeInterdependencies(serviceKeys);

      ManagedServiceGraph managedGraph = ManagedServiceGraph.buildGraph(
              services,
              graph.entries()
      );

      for (Service.Listener serviceListener : serviceListeners) {
        managedGraph.addListener(serviceListener, MoreExecutors.directExecutor());
      }

      return managedGraph;
    }
  }

  private static Multibinder<KeyRef> keyBinder(Binder binder, ServiceLifecycle lifecycle) {
    // guice prevents creating bindings of Key<> to prevent ambiguity, so we wrap our keys in a wrapper
    return Multibinder.newSetBinder(binder, KeyRef.class, lifecycle);
  }

  private static Multibinder<Service> serviceBinder(Binder binder, ServiceLifecycle lifecycle) {
    return Multibinder.newSetBinder(binder, Service.class, lifecycle);
  }

  private static Multibinder<Service.Listener> serviceListenerBinder(Binder binder, ServiceLifecycle lifecycle) {
    return Multibinder.newSetBinder(binder, Service.Listener.class, lifecycle);
  }

  /**
   * simple wrapper to hide bindings of Keys from guice
   */
  @Value.Immutable
  @Identifier
  public static abstract class KeyRef {
    static KeyRef of(Key<? extends Service> serviceKey) {
      return ImmutableKeyRef.of(serviceKey);
    }

    public abstract Key<? extends Service> serviceKey();
  }

}
