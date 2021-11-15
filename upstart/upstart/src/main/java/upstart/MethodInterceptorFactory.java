package upstart;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import upstart.config.UpstartModule;
import upstart.guice.BindingResolver;
import upstart.guice.GuiceDependencyGraph;
import upstart.util.Modifiers;
import upstart.util.Reflect;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

/**
 * With its {@link Binder#bindInterceptor} method, Guice offers support for AOP interception of methods on objects it
 * constructs. However, the built-in implementation doesn't work well with upstart's dependency-aware
 * {@link UpstartModuleExtension#serviceManager() serviceManagement}, because the dependency-relationship between the
 * injected component and its interceptor(s) is hidden from the injector's dependency-graph. This MethodInterceptorFactory
 * interface exists to address that limitation, by facilitating the registration of
 * {@link GuiceDependencyGraph.ExternalDependencyBinder external dependencies} between each intercepted binding and its
 * interceptor's binding.<p/>
 *
 * To introduce AOP interception in an {@link UpstartApplication}, you must:
 * <ol>
 *   <li>Construct an implementation of {@link MethodInterceptorFactory}, which returns an appropriate {@link MethodInterceptor}
 *   instance for each intercepted method passed to its {@link #buildInterceptor} method</li>
 *   <li>Determine appropriate {@link Matcher} policies to select the classes and methods to be intercepted
 *   (often involving utility-methods from the {@link Matchers} class, eg {@link Matchers#annotatedWith})</li>
 *   <li>Register the MethodInterceptorFactory subclass and its Matchers with {@link UpstartModuleExtension#bindInterceptorFactory}</li>
 * </ol>
 * @see UpstartModuleExtension#bindInterceptorFactory
 * @see #DEFAULT_CLASS_MATCHER
 * @see Binder#bindInterceptor
 */
public interface MethodInterceptorFactory {
  /**
   * An optimization: to avoid unnecessary reflection on types that are unlikely to be subject to interception,
   * we offer a default classMatcher that excludes common types.<p/>
   *
   * Note that if your interceptor can be more specific about classes which are candidates for interception, then
   * it will probably be more efficient to provide an appropriate classMatcher via one of the 3-arg overloads of
   * {@link UpstartModuleExtension#bindInterceptorFactory(Matcher, Matcher, Class)}
   */
  Matcher<? super Class<?>> DEFAULT_CLASS_MATCHER =
          Matchers.not(Matchers.inSubpackage("com.google"))
                  .and(Matchers.not(Matchers.inSubpackage("java")));

  /**
   * Implement this method to return a {@link MethodInterceptor} instance appropriate for the given
   * <code>interceptedMethod</code>. The returned object may use dependencies which are {@link Inject @Injected}
   * into the {@link MethodInterceptorFactory} constructor.
   */
  MethodInterceptor buildInterceptor(Class<?> interceptedClass, Method interceptedMethod);

  /**
   * Usually invoked via {@link UpstartModule#bindInterceptorFactory}
   */
  static void bindInterceptorFactory(
          GuiceDependencyGraph.ExternalDependencyBinder externalDependencyBinder,
          Matcher<? super Class<?>> classMatcher,
          Matcher<? super Method> methodMatcher,
          Key<? extends MethodInterceptorFactory> interceptorKey
  ) {
    Binder binder = externalDependencyBinder.binder();
    Provider<? extends MethodInterceptorFactory> factoryProvider = binder.getProvider(interceptorKey);
    InjectedMethodInterceptor injectedInterceptor = new InjectedMethodInterceptor(factoryProvider, classMatcher, methodMatcher, interceptorKey);
    binder.bindInterceptor(classMatcher, methodMatcher, injectedInterceptor);
    externalDependencyBinder.bindDynamicDependency().toInstance(injectedInterceptor);
  }

  class InjectedMethodInterceptor implements MethodInterceptor, GuiceDependencyGraph.DynamicDependencyResolver {
    private final Provider<? extends MethodInterceptorFactory> factoryProvider; // heh, <3 java!
    private final Matcher<? super Class<?>> classMatcher;
    private final Matcher<? super Method> methodMatcher;
    private final Key<? extends MethodInterceptorFactory> key;
    private final Map<Method, MethodInterceptor> interceptors = new HashMap<>();

    public InjectedMethodInterceptor(Provider<? extends MethodInterceptorFactory> factoryProvider, Matcher<? super Class<?>> classMatcher, Matcher<? super Method> methodMatcher, Key<? extends MethodInterceptorFactory> interceptorKey) {
      this.factoryProvider = factoryProvider;
      this.classMatcher = classMatcher;
      this.methodMatcher = methodMatcher;
      this.key = interceptorKey;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      return getInterceptor(invocation.getMethod()).invoke(invocation);
    }

    public MethodInterceptor getInterceptor(Method method) {
      MethodInterceptor interceptor = interceptors.get(method);
      if (interceptor == null) {
        throw new IllegalStateException(String.format("Missing interceptor registration. Interception on guice-managed \"just-in-time\" bindings is error-prone,\n"
                        +"avoid this problem with bind(%s.class) or similar:\n  %s",
                method.getDeclaringClass().getSimpleName(),
                describeMethod(method)));
      }
      return interceptor;
    }

    @Override
    public Stream<Key<?>> computeDependencies(BindingResolver.ResolvedBinding<?> target) {

      Class<?> targetType = target.rawBindingType();
      if (!classMatcher.matches(targetType)) return Stream.empty();
      Set<Method> methods = interceptedMethods(targetType).collect(Collectors.toSet());
      if (methods.isEmpty()) return Stream.empty();

      checkState(target.isConstructedByGuice(), "Interception is only supported on objects created by guice: %s", target);
      checkState(!Modifiers.Final.test(targetType), "Interception cannot be supported on classes marked 'final'", target);

      MethodInterceptorFactory interceptorFactory = factoryProvider.get();
      methods.forEach(m -> interceptors.put(m, interceptorFactory.buildInterceptor(targetType, m)));

      return Stream.of(key);
    }

    private Stream<Method> interceptedMethods(Class<?> bindingClass) {
      return Reflect.allDeclaredMethods(bindingClass, Reflect.LineageOrder.SubclassBeforeSuperclass)
              .filter(methodMatcher::matches)
              .filter(InjectedMethodInterceptor::checkValidInterception)
              .distinct();
    }

    private static boolean checkValidInterception(Method method) {
      if (Modifiers.Private.matches(method) || Modifiers.Final.matches(method)) {
        String description = describeMethod(method);
        throw new IllegalStateException("Interceptors cannot be supported on private or final methods: " + description);
      }
      return true;
    }

    private static String describeMethod(Method method) {
      StringBuilder builder = new StringBuilder();
      for (Annotation annotation : method.getAnnotations()) {
        builder.append('@').append(annotation.annotationType().getSimpleName()).append(' ');
      }
      return builder.append(method.toGenericString()).toString();
    }
  }
}
