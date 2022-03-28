package upstart.proxy;

import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.util.concurrent.Service;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import upstart.config.UpstartModule;
import upstart.guice.GuiceDependencyGraph;
import upstart.guice.TypeLiterals;
import upstart.util.reflect.Reflect;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A mechanism for providing an injectable proxy for a component ({@literal <T>}) which is only lazily resolved
 * when a method on the proxy is invoked for the first time.
 * <p/>
 * This can be convenient for objects which cannot be obtained when injection is being performed by guice
 * (for example, if instantiation requires some {@link Service} to be started first), but will definitely be available
 * by the time the object is first used.
 * <p/>
 * For example, consider a CreditCardService class that provides a BillingProcessor interface, but only after it's
 * been {@link Service#startAsync started}:
 * <pre>
 * {@code
 *   class CreditCardService extends IdleService {
 *     public BillingProcessor newBillingProcessor() {
 *       checkState(isRunning(), "Service wasn't running"); // need to start the service before asking for this
 *       return new BillingProcessor();
 *     }
 *   }
 * }
 * </pre>
 *
 * If a component wants to use a BillingProcessor, we must be careful to refrain from requesting it until after
 * the CreditCardService is started. This demands a deferred ("lazy") initialization pattern:
 *
 * <pre>{@code
 *   class PurchaseProcessor {
 *     private final CreditCardService ccService;
 *     private BillingProcessor billingProcessor;
 *     @Inject
 *     PurchaseProcessor(CreditCardService creditCardService) {
 *       this.ccService = creditCardService;
 *     }
 *
 *     public void purchase(Item item, CreditCard creditCard) {
 *       // lazy init:
 *       if (billingProcessor == null) billingProcessor = ccService.newBillingProcessor();
 *       billingProcessor.bill(item.price(), creditCard);
 *     }
 *   }
 * }</pre>
 *
 * While effective, this can be clumsy and error-prone. We'd prefer to be able to {@link Inject} the PurchaseProcessor
 * directly, and let the lazy initialization be handled elsewhere.
 * <p/>
 * Guice does support this pattern if you arrange a provider-binding to resolve the underlying value, and then
 * {@link Inject} a {@link Provider} of the intended type:
 *
 * <pre>{@code
 *   @Inject Provider<BillingProcessor> billingProcessor
 * }</pre>
 *
 * In fact, using a Provider may be preferable in scenarios where method-call performance is critical (because these
 * dynamic proxies involve reflection). However, working with Providers is still somewhat clumsy and error-prone.
 * To streamline the developer experience, the {@link DynamicProxyBindingBuilder} allows us to
 * {@link Inject @Inject} a <em>proxy</em> the BillingProcessor, which is lazily initialized the first time we use it:
 *
 * <pre>{@code
 *   class PurchaseModule extends UpstartModule {
 *      protected void configure() {
 *        bindMemoizingProxy(BillingProcessor.class)
 *            .initializedFrom(CreditCardService.class, CreditCardService::newBillingProcessor);
 *      }
 *   }
 *
 *   class PurchaseProcessor {
 *     private final BillingProcessor billingProcessor; // this is a proxy!
 *
 *     @Inject
 *     PurchaseProcessor(BillingProcessor billingProcessorProxy) {
 *       this.billingProcessor = billingProcessorProxy;
 *     }
 *
 *     public void purchase(Item item, CreditCard creditCard) {
 *       // lazy init is handled by the proxy
 *       billingProcessor.bill(item.price(), creditCard);
 *     }
 *   }
 * }</pre>
 *
 * Alternatively, if the initialization of the lazy value is complex or involves multiple dependencies,
 * you may arrange a binding to an alternate {@link Key} which {@link #suppliedBy supplies} the underlying value.
 * <p/>
 * Finally, a conventional style is supported using the {@link Lazy} and {@link LazyProvider} annotations, via
 * {@link #bindLazyProviderProxy}:
 * <pre>{@code
 *   class PurchaseModule extends UpstartModule {
 *      protected void configure() {
 *        bindLazyProviderProxy(BillingProcessor.class);
 *        // identical to:
 *        // bindMemoizingProxy(Key.get(BillingProcessor.class, Lazy.class))
 *        //    .suppliedBy(Key.get(BillingProcessor.class, LazyProvider.class));
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
 *
 *
 * @see #bindDynamicProxy(Binder, Class)
 * @see #bindDynamicProxy(Binder, Key)
 * @see #bindLazyProviderProxy(Binder, Class)
 * @see UpstartModule#bindLazyProviderProxy
 * @see UpstartModule#bindDynamicProxy
 *
 */
public class DynamicProxyBindingBuilder<T> {
  private final Binder binder;
  private final Key<T> proxiedKey;

  /**
   * Starts a binding for a proxy of the given type
   * @see DynamicProxyBindingBuilder
   */
  public static <T> DynamicProxyBindingBuilder<T> bindDynamicProxy(Binder binder, Class<T> proxiedType) {
    return bindDynamicProxy(binder, Key.get(proxiedType));
  }

  /**
   * Starts a binding for a proxy that can be injected via the given proxiedKey
   * @see DynamicProxyBindingBuilder
   */
  public static <T> DynamicProxyBindingBuilder<T> bindDynamicProxy(Binder binder, Key<T> proxiedKey) {
    return new DynamicProxyBindingBuilder<>(binder, proxiedKey);
  }

  private DynamicProxyBindingBuilder(Binder binder, Key<T> proxiedKey) {
    this.binder = binder;
    this.proxiedKey = proxiedKey;
  }

  /**
   * Installs a proxy for {@link T} which is lazily obtained by applying the given initializer to the
   * value identified by the injectedSourceKey.
   * <p/>
   * The first time a method on the generated proxy is invoked, the initialization will be performed, and
   * the result will be {@link Suppliers#memoize memoized}.
   * <p/>
   * Components which inject the {@link #proxiedKey} will incur a dependency on the injectedSourceKey via
   * upstart's {@link GuiceDependencyGraph.ExternalDependencyBinder}.
   * @param injectedSourceKey the key identifying the input needed to produce the real {@link T} to be resolved by the generated proxy
   * @param initializer the method to transform the injectedSourceKey into the value to be proxied
   */
  public <S> void initializedFrom(Class<S> injectedSourceKey, Function<? super S, ? extends T> initializer, Class<?>... constructorSignature) {
    initializedFrom(Key.get(injectedSourceKey), initializer, constructorSignature);
  }

  /**
   * Installs a proxy for {@link T} which is lazily obtained by applying the given initializer to the
   * value identified by the injectedSourceKey.
   * <p/>
   * The first time a method on the generated proxy is invoked, the initialization will be performed, and
   * the result will be {@link Suppliers#memoize memoized}.
   * <p/>
   * Components which inject the {@link #proxiedKey} will incur a dependency on the injectedSourceKey via
   * upstart's {@link GuiceDependencyGraph.ExternalDependencyBinder}.
   * @param injectedSourceKey the key identifying the input needed to produce the real {@link T} to be resolved by the generated proxy
   * @param initializer the method to transform the injectedSourceKey into the value to be proxied
   */
  public <S> void initializedFrom(Key<S> injectedSourceKey, Function<? super S, ? extends T> initializer, Class<?>... constructorSignature) {
    bindMemoizingProxy(binder, injectedSourceKey, proxiedKey, initializer, constructorSignature);
  }

  public void suppliedBy(Class<? extends T> alternateKey, Annotation annotation) {
    suppliedBy(Key.get(alternateKey, annotation));
  }

  public void suppliedBy(Class<? extends T> alternateKey, Class<? extends Annotation> annotation) {
    suppliedBy(Key.get(alternateKey, annotation));
  }

  public void suppliedBy(Key<? extends T> alternateKey) {
    checkArgument(!alternateKey.equals(proxiedKey), "Alternate key must differ from proxied key", alternateKey);
    bindMemoizingProxy(binder, alternateKey, proxiedKey, Function.identity());
  }

  public DynamicProxyBindingBuilder<T> annotatedWith(Class<? extends Annotation> annotationType) {
    return new DynamicProxyBindingBuilder<>(binder, Key.get(proxiedKey.getTypeLiteral(), annotationType));
  }

  public DynamicProxyBindingBuilder<T> annotatedWith(Annotation annotation) {
    return new DynamicProxyBindingBuilder<>(binder, Key.get(proxiedKey.getTypeLiteral(), annotation));
  }

  private static final LoadingCache<Method, Method> ACCESSIBLE_METHODS = CacheBuilder.newBuilder()
          .build(CacheLoader.from(Reflect::setAccessible));

  public static <S, T> void bindMemoizingProxy(Binder binder, Class<S> injectedSource, Class<T> lazyType, Function<? super S, ? extends T> initializer) {
    bindMemoizingProxy(binder, Key.get(injectedSource), Key.get(lazyType), initializer);
  }

  public static <T> void bindMemoizingProxy(Binder binder, Key<T> lazyKey, Key<? extends T> eagerKey) {
    bindMemoizingProxy(binder, eagerKey, lazyKey, Function.identity());
  }

  public static <S, T> void bindMemoizingProxy(Binder binder, Key<S> injectedSource, Key<T> lazyKey, Function<? super S, ? extends T> initializer, Class<?>... constructorSignature) {
    Provider<S> sourceProvider = binder.getProvider(injectedSource);

    GuiceDependencyGraph.externalDependencyBinder(binder).bindExternalDependency(lazyKey).dependsUpon(injectedSource);

    bindDynamicProxy(binder, lazyKey, Suppliers.memoize(() -> initializer.apply(sourceProvider.get())), constructorSignature);
  }

  @SuppressWarnings("unchecked")
  public static <T> void bindDynamicProxy(Binder binder, Key<T> key, Supplier<T> instanceSupplier, Class<?>... constructorSignature) {
    Class<T> proxiedInterface = TypeLiterals.getRawType(key.getTypeLiteral());

    T proxy = Proxies.createProxy(proxiedInterface, new ProxyInterceptor(instanceSupplier));

    binder.bind(key).toInstance(proxy);
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
   *
   * @see UpstartModule#bindLazyProviderProxy
   */
  public static void bindLazyProviderProxy(Binder binder, Class<?> type) {
    bindLazyProviderProxy(binder, TypeLiteral.get(type));
  }

  public static <T> void bindLazyProviderProxy(Binder binder, TypeLiteral<T> key) {
    bindMemoizingProxy(binder, Key.get(key, Lazy.class), Key.get(key, LazyProvider.class));
  }

  public static class ProxyInterceptor extends AbstractInvocationHandler {
    private final Supplier<?> instanceSupplier;

    public ProxyInterceptor(Supplier<?> instanceSupplier) {
      this.instanceSupplier = instanceSupplier;
    }

    @Override
    public Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        return ACCESSIBLE_METHODS.getUnchecked(method).invoke(instanceSupplier.get(), args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}
