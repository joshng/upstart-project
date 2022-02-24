package upstart.guice;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import upstart.UpstartModuleExtension;
import upstart.config.UpstartModule;
import upstart.util.Pair;
import upstart.util.PairStream;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;


@Singleton
public class GuiceDependencyGraph {
  private final ExternalDependencies externalDependencies;

  public static ExternalDependencyBinder externalDependencyBinder(Binder binder) {
    return new ExternalDependencyBinder(binder);
  }

  private final BindingResolver resolver;

  @Inject
  public GuiceDependencyGraph(BindingResolver resolver, ExternalDependencies externalDependencies) {
    this.resolver = resolver;
    this.externalDependencies = externalDependencies;
  }

  public <T> Multimap<T, T> computeInterdependencies(
          Collection<Key<? extends T>> keys
  ) {

    externalDependencies.ensureInitialized();

    Set<? extends BindingResolver.ResolvedBinding<? extends T>> resolvedBindings = keys.stream()
            .map(resolver::resolve)
            .collect(Collectors.toSet());
    Map<Binding<?>, T> instancesByBinding = resolvedBindings.stream()
            .collect(Collectors.toMap(
                    BindingResolver.ResolvedBinding::binding,
                    BindingResolver.ResolvedBinding::getInstance,
                    (a, b) -> { throw new IllegalArgumentException("duplicate: " + a);},
                    // use IdentityHashMap because binding.equals is incorrect for similar bindings from PrivateModules
                    IdentityHashMap::new)
            );

    Function<Binding<?>, T> getBoundInstance = Functions.forMap(instancesByBinding);
    return resolvedBindings.stream()
            .collect(ImmutableListMultimap.flatteningToImmutableListMultimap(
                    BindingResolver.ResolvedBinding::getInstance,
                    binding -> {
                      Set<BindingResolver.ResolvedBinding<?>> transitiveDependencies = binding.findNearestDependencies(
                              externalDependencies,
                              b -> !b.equals(binding) && instancesByBinding.containsKey(b.binding())
                      );
                      return transitiveDependencies.stream()
                              .map(BindingResolver.ResolvedBinding::binding)
                              .map(getBoundInstance);
                    }));
  }

  public boolean isSingleton(Key<?> key) {
    return resolver.isSingleton(key);
  }

  public static class ExternalDependencyBinder implements UpstartModuleExtension {
    private final Binder binder;
    private final Multibinder<ExternalDependency> depBinder;

    ExternalDependencyBinder(Binder binder) {
      this.binder = binder;
      depBinder = dependencyMultibinder(binder);
    }

    @Override
    public Binder binder() {
      return binder;
    }

    public DependencyBindingBuilder bindExternalDependency(Class<?> dependentClass) {
      return bindExternalDependency(Key.get(dependentClass));
    }

    public DependencyBindingBuilder bindExternalDependency(Key<?> dependentKey) {
      return new DependencyBindingBuilder(dependentKey);
    }

    public LinkedBindingBuilder<DynamicDependencyResolver> bindDynamicDependency() {
      return dynamicDependencyMultibinder(binder).addBinding();
    }

    public void requireBinding(Key<?> dependencyKey) {
      binder.getProvider(dependencyKey);
    }

    public class DependencyBindingBuilder {

      private final Key<?> dependentKey;

      DependencyBindingBuilder(Key<?> dependentKey) {
        this.dependentKey = dependentKey;
      }

      public final ExternalDependencyBinder dependsUpon(Class<?> dependencyClass) {
        return dependsUpon(Key.get(dependencyClass));
      }

      public final ExternalDependencyBinder dependsUpon(Class<?>... dependencyClasses) {
        return dependsUpon(Stream.of(dependencyClasses).map(Key::get).toArray(Key<?>[]::new));
      }

      public final ExternalDependencyBinder dependsUpon(Key<?>... dependencyKeys) {
        if (dependencyKeys.length > 0) {
          requireBinding(dependentKey);
          for (Key<?> dependencyKey : dependencyKeys) {
            requireBinding(dependencyKey);
            depBinder.addBinding().toInstance(new ExternalDependency(dependentKey, dependencyKey));
          }
        }
        return ExternalDependencyBinder.this;
      }

      @Override
      public String toString() {
        return "DependencyBindingBuilder{" +
                "dependentKey=" + dependentKey +
                '}';
      }
    }
  }

  @Singleton
  static class ExternalDependencies {
    private final Set<ExternalDependency> externalDependencies;
    private final Set<DynamicDependencyResolver> dynamicResolvers;
    private final Injector injector;
    private final BindingResolver resolver;

    private SetMultimap<BindingResolver.ResolvedBinding<?>, BindingResolver.ResolvedBinding<?>> resolvedBindings;

    @Inject
    ExternalDependencies(Set<ExternalDependency> externalDependencies, Set<DynamicDependencyResolver> dynamicResolvers, Injector injector, BindingResolver resolver) {
      this.externalDependencies = externalDependencies;
      this.dynamicResolvers = dynamicResolvers;
      this.injector = injector;
      this.resolver = resolver;
    }

    Set<BindingResolver.ResolvedBinding<?>> resolvedDependencies(BindingResolver.ResolvedBinding<?> dependentKey) {
      return checkNotNull(resolvedBindings, "ExternalDependencies were not initialized").get(dependentKey);
    }

    synchronized void ensureInitialized() {
      if (resolvedBindings == null) {
        Stream<BindingResolver.ResolvedBinding<?>> allResolvedBindings = injector.getAllBindings()
                .keySet()
                .stream()
                .<BindingResolver.ResolvedBinding<?>>map(resolver::resolve)
                .distinct();

        PairStream<BindingResolver.ResolvedBinding<?>, BindingResolver.ResolvedBinding<?>> dynamicDeps = PairStream.cartesianProduct(
                allResolvedBindings,
                dynamicResolvers::stream
        ).flatMapValues((target, dd) -> dd.computeDependencies(target).map(resolver::resolve));

        resolvedBindings = PairStream.of(
                Stream.concat(
                        externalDependencies.stream().map(ed -> ed.resolve(resolver)),
                        dynamicDeps
                )
        ).toImmutableSetMultimap();
      }
    }
  }

  private static class ExternalDependency {
    final Key<?> dependentKey;
    final Key<?> dependencyKey;

    ExternalDependency(Key<?> dependentKey, Key<?> dependencyKey) {
      this.dependentKey = dependentKey;
      this.dependencyKey = dependencyKey;
    }

    Pair<BindingResolver.ResolvedBinding<?>, BindingResolver.ResolvedBinding<?>> resolve(BindingResolver resolver) {
      return Pair.of(resolver.resolve(dependentKey), resolver.resolve(dependencyKey));
    }
  }

  private static Multibinder<ExternalDependency> dependencyMultibinder(Binder binder) {
    return Multibinder.newSetBinder(binder, ExternalDependency.class);
  }

  private static Multibinder<DynamicDependencyResolver> dynamicDependencyMultibinder(Binder binder) {
    return Multibinder.newSetBinder(binder, DynamicDependencyResolver.class);
  }

  public interface DynamicDependencyResolver {
    /**
     * Responsible for determining {@link Key Keys} to be considered "external dependencies" of
     * the given <code>target</code> binding (if any).<p/>
     *
     * Note that this method will be invoked for every {@link BindingResolver.ResolvedBinding} present in the guice injector, and
     * should promptly return {@link Stream#empty} for bindings which are not relevant to this resolver.
     */
    Stream<Key<?>> computeDependencies(BindingResolver.ResolvedBinding<?> target);
  }

  public static class GuiceModule extends UpstartModule {
    @Override
    protected void configure() {
      bind(GuiceDependencyGraph.class).asEagerSingleton();
      dependencyMultibinder(binder());
      dynamicDependencyMultibinder(binder());
    }
  }
}
