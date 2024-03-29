package upstart;

import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matcher;
import upstart.config.ConfigKey;
import upstart.config.UpstartContext;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartConfigBinder;
import upstart.config.UpstartConfigProvider;
import upstart.guice.GuiceDependencyGraph;
import upstart.managedservices.ResourceProviderService;
import upstart.proxy.DynamicProxyBindingBuilder;
import upstart.proxy.Lazy;
import upstart.proxy.LazyProvider;
import upstart.util.concurrent.services.ExecutionThreadService;
import upstart.util.concurrent.services.IdleService;
import upstart.util.concurrent.services.InitializingService;
import upstart.managedservices.ManagedServicesModule;
import upstart.util.concurrent.services.NotifyingService;
import upstart.util.concurrent.services.ScheduledService;
import upstart.util.reflect.Reflect;
import org.aopalliance.intercept.MethodInterceptor;

import javax.inject.Inject;
import java.lang.reflect.Method;

public interface UpstartModuleExtension {
  Binder binder();

  default UpstartContext upstartContext() {
    return bindConfig(UpstartContext.class);
  }

  /**
   * Arrange for the given configClass (which <b>must</b> be annotated with {@link ConfigPath}) to be available for
   * injection without any annotations.
   */
  default <T> T bindConfig(Class<T> configClass) {
    return configBinder().bindConfig(binder(), configClass);
  }

  default <T> T bindConfig(String configPath, Class<T> configClass) {
    return bindConfig(configPath, TypeLiteral.get(configClass));
  }

  default <T> T bindConfig(String configPath, TypeLiteral<T> configType) {
    return bindConfig(configPath, Key.get(configType));
  }

  default <T> T bindConfig(String configPath, Key<T> key) {
    return bindConfig(configPath, key.getTypeLiteral(), key);
  }

  default <T> T bindConfig(String configPath, Class<T> type, Key<? super T> key) {
    return bindConfig(ConfigKey.of(configPath, type), key);
  }

  default <T> T bindConfig(String configPath, TypeLiteral<T> type, Key<? super T> key) {
    return bindConfig(ConfigKey.of(configPath, type), key);
  }

  default <T> T bindConfig(ConfigKey<T> configKey, Key<? super T> key) {
    return configBinder().bindConfig(binder(), configKey, key);
  }

  /**
   * Obtains the {@link ManagedServicesModule.ServiceManager} for managing the lifecycles of {@link Service} classes.
   * <p/>
   * Services managed by this facility will be {@link Service#startAsync started} and {@link Service#stopAsync stopped}
   * in the correct order to ensure that no Service is {@link Service.State#RUNNING RUNNING} when any other Service
   * injected into it as a guice <em>dependency</em> via {@link Inject @Inject} (directly or transitively)
   * is <b>NOT</b> RUNNING.
   *
   * @see IdleService
   * @see ExecutionThreadService
   * @see InitializingService
   * @see ScheduledService
   * @see NotifyingService
   */
  default ManagedServicesModule.ServiceManager serviceManager() {
    return ManagedServicesModule.serviceManager(sourceCleanedBinder());
  }

  /**
   * Provides a facility for declaring that a given component "depends upon" another component, even if
   * there is no visible linkage in the guice injection-graph between them. This is sometimes needed to ensure
   * that {@link Service services} managed by the {@link #serviceManager} are started/stopped in the correct order when
   * they have dependency-relationships that cannot be inferred from the guice injection-graph.<p/>
   *
   * Examples of scenarios that might require this include:
   * <ol>
   *   <li>Components that are integrated via {@link Binder#bindInterceptor}: objects which have intercepted methods
   *       effectively depend upon the dependencies of the interceptor, but this relationship is not explicitly
   *       visible as a guice {@link Inject injection-point}. (Note that this is handled automatically by
   *       {@link MethodInterceptorFactory}/{@link #bindInterceptorFactory}.)</li>
   *   <li>Components whose startup or shutdown depend upon external conditions that are reflected by some other
   *       component's lifecycle. For example, if a DatabaseService starts up an external database, and
   *       a DaoService then connects to that database (and should thus wait for the DatabaseService to be started),
   *       this could be arranged as follows:
   *       <pre>
   *         externalDependencyBinder()
   *         &nbsp;&nbsp;.bindExternalDependency(DaoService.class)
   *         &nbsp;&nbsp;.dependsUpon(DatabaseService.class)
   *       </pre>
   *       } </li>
   * </ol>
   *
   * Note that the need for such explicit dependency-declarations can often be avoided (usually with beneficial
   * architectural consequences) by arranging some way for the dependent service to use the dependency explicitly.
   * For the example above, the DatabaseService could offer a method for connecting to its database, and the
   * DaoService could <code>@Inject DatabaseService</code> to obtain its connection as part of its
   * {@link Service#startAsync startUp} routine.
   */
  default GuiceDependencyGraph.ExternalDependencyBinder externalDependencyBinder() {
    return GuiceDependencyGraph.externalDependencyBinder(sourceCleanedBinder());
  }

  /**
   * Registers a {@link MethodInterceptorFactory} using the {@link MethodInterceptorFactory#DEFAULT_CLASS_MATCHER default
   * class-matcher}.
   *
   * Note that if your interceptor can be more specific about classes which are candidates for interception, then
   * it will probably be more efficient to provide an appropriate classMatcher via one of the 3-arg overloads of
   * {@link UpstartModuleExtension#bindInterceptorFactory(Matcher, Matcher, Class)}
   * @see #bindInterceptorFactory(Matcher, Matcher, Key)
   */
  default void bindInterceptorFactory(Matcher<? super Method> methodMatcher, Class<? extends MethodInterceptorFactory> interceptorClass) {
    bindInterceptorFactory(MethodInterceptorFactory.DEFAULT_CLASS_MATCHER, methodMatcher, interceptorClass);
  }

  /**
   * @see #bindInterceptorFactory(Matcher, Matcher, Key)
   */
  default void bindInterceptorFactory(Matcher<? super Class<?>> classMatcher, Matcher<? super Method> methodMatcher, Class<? extends MethodInterceptorFactory> interceptorClass) {
    bindInterceptorFactory(classMatcher, methodMatcher, Key.get(interceptorClass));
  }

  /**
   * Dependency-aware {@link MethodInterceptor} registration: use this method instead of {@link Binder#bindInterceptor}
   * (and instead of {@link AbstractModule#bindInterceptor}) to configure AOP interception of matching methods with
   * correct dependency-tracking to support upstart's {@link #serviceManager service-lifecycle management}.<p/>
   *
   * As a result of this binding, any methods on objects constructed by guice that match the given {@link Matcher Matchers}
   * will be intercepted with an interceptor initialized via {@link MethodInterceptorFactory#buildInterceptor}. In addition,
   * those objects will inherit {@link #externalDependencyBinder() external dependencies}
   * for all of the dependencies of the given <code>interceptorKey</code>.
   */

  default void bindInterceptorFactory(
          Matcher<? super Class<?>> interceptedClass,
          Matcher<? super Method> methodMatcher,
          Key<? extends MethodInterceptorFactory> interceptorKey
  ) {
    MethodInterceptorFactory.bindInterceptorFactory(externalDependencyBinder(), interceptedClass, methodMatcher, interceptorKey);
  }

  default UpstartConfigProvider configProvider() {
    return configBinder().configProvider();
  }

  /**
   * Starts a binding for a proxy of the given type
   * @see DynamicProxyBindingBuilder
   */
  default <T> DynamicProxyBindingBuilder<T> bindDynamicProxy(Class<T> proxiedType) {
    return DynamicProxyBindingBuilder.bindDynamicProxy(binder(), proxiedType);
  }

  /**
   * Starts a binding for a proxy that can be injected via the given proxiedKey
   * @see DynamicProxyBindingBuilder
   */
  default <T> DynamicProxyBindingBuilder<T> bindDynamicProxy(Key<T> proxiedKey) {
    return DynamicProxyBindingBuilder.bindDynamicProxy(binder(), proxiedKey);
  }

  default <T> Key<T> bindResourceFromProviderService(Class<? extends ResourceProviderService<T>> providerServiceClass) {
    return bindResourceFromProviderService(Key.get(providerServiceClass));
  }

  default <T> Key<T> bindResourceFromProviderService(Key<? extends ResourceProviderService<T>> serviceKey) {
    Class<T> providedType = (Class<T>) TypeToken.of(serviceKey.getTypeLiteral().getType()).resolveType(ResourceProviderService.class.getTypeParameters()[0]).getRawType();
    Key<T> proxiedKey = serviceKey.ofType(providedType);
    bindDynamicProxy(proxiedKey).initializedFrom(serviceKey, ResourceProviderService::getResource);
    serviceManager().manage(serviceKey);
    return proxiedKey;
  }

  /**
   * Arranges a proxy that can be injected to an injection-point annotated with {@link Lazy}, which will be lazily
   * resolved according to a corresponding binding annotated with {@link LazyProvider}.
   * <p/>
   * For example:
   * <pre>{@code
   *   class PurchaseModule extends UpstartModule {
   *      protected void configure() {
   *        bindLazyProviderProxy(BillingProcessor.class);
   *      }
   *
   *     @Provides @LazyProvider BillingProcessor provideBillingProcessor(CreditCardService ccService) {
   *       return ccService.newBillingProcessor();
   *     }
   *   }
   *
   *   class PurchaseProcessor {
   *     private final BillingProcessor billingProcessor; // this is a proxy!
   *
   *     @Inject
   *     PurchaseProcessor(@Lazy BillingProcessor billingProcessorProxy) {
   *       this.billingProcessor = billingProcessorProxy;
   *     }
   *
   *     public void purchase(Item item, CreditCard creditCard) {
   *       // lazy init is handled by the proxy
   *       billingProcessor.bill(item.price(), creditCard);
   *     }
   *   }
   * }</pre>
   */
  default <T> void bindLazyProviderProxy(Class<T> proxiedClass) {
    DynamicProxyBindingBuilder.bindLazyProviderProxy(binder(), proxiedClass);
  }

  /** @see #bindLazyProviderProxy(Class) */
  default <T> void bindLazyProviderProxy(TypeLiteral<T> proxiedClass) {
    DynamicProxyBindingBuilder.bindLazyProviderProxy(binder(), proxiedClass);
  }

  default UpstartConfigBinder configBinder() {
    return UpstartConfigBinder.get();
  }

  private Binder sourceCleanedBinder() {
    return binder().skipSources(UpstartModuleExtension.class);
  }
}
