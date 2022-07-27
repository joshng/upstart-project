package upstart;

import com.google.common.truth.ThrowableSubject;
import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.util.Modules;
import io.upstartproject.hojack.ConfigMapper;
import upstart.config.EnvironmentConfigBuilder;
import upstart.config.HojackConfigProvider;
import upstart.config.UpstartConfigBinder;
import upstart.config.UpstartConfigProvider;
import upstart.config.UpstartEnvironment;
import upstart.managedservices.ManagedServicesModule;
import upstart.test.truth.CompletableFutureSubject;
import upstart.test.UpstartExtension;
import upstart.test.UpstartTestBuilder;
import upstart.test.SingletonExtension;
import upstart.util.reflect.Reflect;
import upstart.util.exceptions.ThrowingConsumer;
import upstart.util.concurrent.FutureSuccessTracker;
import upstart.util.concurrent.Promise;
import upstart.util.exceptions.Exceptions;
import com.typesafe.config.Config;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.util.collect.Optionals;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertWithMessage;
import static upstart.managedservices.ManagedServicesModule.KeyRef;

public class InternalTestBuilder implements UpstartTestBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(InternalTestBuilder.class);
  private static final Constructor<UpstartService.Builder> BUILDER_CONSTRUCTOR;

  static {
    UpstartExtension.ensureInitialized();
    try {
      BUILDER_CONSTRUCTOR = UpstartService.Builder.class.getDeclaredConstructor(UpstartConfigProvider.class);
      BUILDER_CONSTRUCTOR.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private final Set<Module> installedModules = new HashSet<>();
  private final Set<Module> overrideModules = new HashSet<>();
  private final Set<Key<? extends Service>> suppressedServiceKeys = new HashSet<>();
  private final Promise<Injector> injectorPromise = new Promise<>();
  private final Promise<Injector> afterInjectionPromise = new Promise<>();
  private final FutureSuccessTracker callbackSuccessTracker = new FutureSuccessTracker();
  private final EnvironmentConfigBuilder configBuilder;
  private final FutureTask<Injector> injectorBuilderTask;
  private Optional<Consumer<? super CompletableFuture<Service.State>>> shutdownVisitor = Optional.empty();

  public InternalTestBuilder(EnvironmentConfigBuilder configBuilder) {
    this.configBuilder = configBuilder;
    injectorBuilderTask = new FutureTask<>(() -> {
      callbackSuccessTracker.setNoMoreJobs();
      HojackConfigProvider configProvider = configBuilder.buildConfigProvider();
      installModule(new UpstartService.Builder.UpstartCoreModule());
      for (String testModule : configProvider.getStringList("upstart.test.installModules")) {
        overrideBindings(Reflect.newInstance(testModule, Module.class));
      }
      UpstartService.Builder realBuilder = BUILDER_CONSTRUCTOR.newInstance(configProvider);
      realBuilder.installModule(buildEffectiveModule(configProvider, installedModules));
      return injectorPromise.tryComplete(realBuilder::buildInjector).join();
    });
  }

  private Module buildEffectiveModule(HojackConfigProvider upstartConfig, Collection<Module> installedModules) {
    Module overriddenModule = Modules.override(installedModules).with(overrideModules);
    return suppressServiceManagement(upstartConfig, overriddenModule);
  }

  private Module suppressServiceManagement(HojackConfigProvider upstartConfig, Module fullModule) {
    if (suppressedServiceKeys.isEmpty()) return fullModule;

    // this is awkward: managed services are added to a multibinder, and we want to remove them here for testing.
    // guice doesn't support removing items from a multibinder, so we go behind the scenes to construct a new module
    // with everything from the original module EXCEPT for the unwanted elements.
    List<Element> originalElements = new ArrayList<>(
            UpstartConfigBinder.withBinder(upstartConfig, () -> Elements.getElements(fullModule))
    );
    return Elements.getModule(originalElements.stream().filter(this::unsuppressedServiceBinding)::iterator);
  }

  public static InternalTestBuilder getInstance(ExtensionContext context) {
    return (InternalTestBuilder) SingletonExtension.getOrCreateContextFrom(UpstartExtension.class, context);
  }

  public <T> T getInstance(Key<T> key) {
    return getInjector().getInstance(key);
  }

  public <T> T getInstance(Class<T> type) {
    return getInjector().getInstance(type);
  }

  @Override
  public UpstartTestBuilder installModule(Module module) {
    return ensuringUnfrozen(() -> installedModules.add(module));
  }

  @Override
  public ConfigMapper configMapper() {
    return configBuilder.configMapper();
  }

  @Override
  public Optional<String> relativePath() {
    return configBuilder.relativePath();
  }

  @Override
  public EnvironmentConfigBuilder rootConfigBuilder() {
    return configBuilder.rootConfigBuilder();
  }

  public InternalTestBuilder overrideBindings(Module overrides) {
    return ensuringUnfrozen(() -> overrideModules.add(overrides));
  }

  @Override
  public UpstartTestBuilder overrideConfig(Config config) {
    return ensuringUnfrozen(() -> UpstartTestBuilder.super.overrideConfig(config));
  }

  @Override
  public InternalTestBuilder disableServiceManagement(Key<? extends Service> serviceKey) {
    return ensuringUnfrozen(() -> suppressedServiceKeys.add(serviceKey));
  }

  @Override
  public InternalTestBuilder expectShutdownException(Class<? extends Throwable> exceptionType) {
    return assertShutdownException(exceptionSubject -> exceptionSubject.isInstanceOf(exceptionType));
  }

  @Override
  public InternalTestBuilder assertShutdownException(ThrowingConsumer<ThrowableSubject> assertion) {
    return whenShutDown(future -> assertion.acceptOrThrow(
            assertWithMessage("Shutdown exception")
                    .about(CompletableFutureSubject.<Service.State>completableFutures())
                    .that(future)
                    .completedWithExceptionThat()));
  }

  @Override
  public InternalTestBuilder whenShutDown(ThrowingConsumer<? super CompletableFuture<Service.State>> visitor) {
    shutdownVisitor = Optional.of(visitor);
    return this;
  }

  public Optional<Consumer<? super CompletableFuture<Service.State>>> shutdownVisitor() {
    return shutdownVisitor;
  }

  @Override
  public synchronized InternalTestBuilder withInjector(ThrowingConsumer<? super Injector> callback) {
    Promise<Void> result = afterInjectionPromise.thenAccept(callback);
    if (result.isDone()) {
      result.join();
    } else {
      callbackSuccessTracker.track(result);
    }
    return this;
  }

  public synchronized Injector getInjector() {
    injectorBuilderTask.run();
    try {
      return injectorBuilderTask.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw Exceptions.throwUnchecked(e.getCause());
    }
  }

  public void invokeAfterInjection() {
    Injector injector = getInjector();
    LOG.info("Test service-graph: {}", injector.getInstance(ManagedServicesModule.INFRASTRUCTURE_GRAPH_KEY));
    afterInjectionPromise.completeWith(injectorPromise);
    callbackSuccessTracker.completionFuture().join();
  }

  private InternalTestBuilder ensuringUnfrozen(Runnable block) {
    checkUnfrozen();
    block.run();
    return this;
  }

  private void checkUnfrozen() {
    if (injectorPromise.isDone()) throw new IllegalStateException("Test fixture was already built");
  }

  private boolean unsuppressedServiceBinding(Element element) {
    return Optionals.asInstance(element, InstanceBinding.class)
            .map(InstanceBinding::getInstance)
            .filter(KeyRef.class::isInstance)
            .map(KeyRef.class::cast)
            .map(KeyRef::serviceKey)
            .filter(suppressedServiceKeys::contains)
            .isEmpty()
            && Optionals.asInstance(element, LinkedKeyBinding.class)
            .filter(InternalTestBuilder::isServiceBinding)
            .filter(linkedBinding -> suppressedServiceKeys.contains(linkedBinding.getLinkedKey()))
            .isEmpty();
  }

  private static boolean isServiceBinding(LinkedKeyBinding<?> linkedKeyBinding) {
    Key<?> key = linkedKeyBinding.getKey();
    // kludge: this is what a Multibinder.newSetBinder binding looks like
    return key.getTypeLiteral().getRawType() == Service.class
            && key.getAnnotationType().getName().equals("com.google.inject.internal.Element");
  }
}
