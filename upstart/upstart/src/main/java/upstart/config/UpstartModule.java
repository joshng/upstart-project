package upstart.config;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.matcher.Matcher;
import upstart.UpstartService;
import upstart.UpstartModuleExtension;
import upstart.managedservices.ManagedServicesModule;
import org.aopalliance.intercept.MethodInterceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A utility base-class for configuring {@link upstart.UpstartApplication UpstartApplications}.
 * <p/>
 * Provides convenient access to the methods in {@link UpstartModuleExtension}, as well as class-based deduplication
 * to prevent multiple copies of identical configurations from being installed in the same application.
 * <p/>
 * The use of this class is optional (but convenient!); the methods defined here delegate to the
 * {@link UpstartConfigBinder} and {@link ManagedServicesModule#serviceManager}, which can also be invoked directly if
 * desired.
 *
 * @see AbstractModule
 * @see UpstartModuleExtension
 * @see #deduplicateBy
 */
public abstract class UpstartModule extends AbstractModule implements UpstartModuleExtension {
  private final List<Object> identity = new ArrayList<>();

  /**
   * we override `configure` here just to better surface its IDE autocompletion
   */
  @Override
  protected void configure() {
  }

  public UpstartModule() {
  }


  /**
   * Passes the provided values to {@link #deduplicateBy}, for deduplication of identical instances installed in the
   * same application.
   *
   * @param identityInputs the inputs which uniquely determine the behavior of this UpstartModule,
   *                       used to deduplicate identically-configured instances
   * @see #deduplicateBy
   * @see #equals
   */
  protected UpstartModule(Object... identityInputs) {
    deduplicateBy(identityInputs);
  }


  /**
   * we override `binder` to expose it as public, so that mixin-interfaces can access it
   */
  public Binder binder() {
    return super.binder().skipSources(UpstartModule.class);
  }

  /**
   * @deprecated this method interfere's with upstart's service-lifecycle management; use {@link #bindInterceptorFactory} instead.
   */
  @Deprecated
  @Override
  protected void bindInterceptor(Matcher<? super Class<?>> classMatcher, Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    throw new UnsupportedOperationException("bindInterceptor() is unsupported by upstart. Use bindInterceptorFactory instead. (See UpstartModule#bindInterceptor javadoc for more info)");
  }

  /**
   * By default, each UpstartModule subclass is treated as a singleton: if multiple instances of the same type
   * are installed an application, they are silently deduplicated (via their implementation of {@link Object#equals}).
   * <p/>
   * Modules which are not meant to be singletons may provide configuration-values to differentiate instances of
   * the same class whose guice-bindings vary depending upon those values. This way, redundant installations with
   * identical inputs will be deduplicated, but instances with differing values will all be installed.
   * <p/>
   * This method should be invoked prior to installing or otherwise exposing UpstartModule object (ie, from its constructor).
   *
   * @param identityInputs the inputs which uniquely determine the behavior of this UpstartModule,
   *                       used to deduplicate identically-configured instances
   * @see #UpstartModule(Object...)
   * @see #equals
   */
  protected void deduplicateBy(Object... identityInputs) {
    identity.addAll(Arrays.asList(identityInputs));
  }

  /**
   * Unlike guice's built-in {@link AbstractModule}, UpstartModules are considered equal if they are the same CLASS, and
   * present the same values to {@link #deduplicateBy} (if any).
   *
   * This prevents multiple copies of the same type of UpstartModule from being erroneously
   * {@link Binder#install installed} into the same {@link UpstartService} (which can easily happen if
   * a group of Modules form a "diamond" dependency-pattern).
   * <p/>
   * In scenarios where multiple differently-configured copies of the same type of UpstartModule are desired, the
   * UpstartModule subclass must either provide all configured values via {@link #deduplicateBy} to distinguish unique
   * instances, or override {@link #equals}/{@link #hashCode()} as appropriate.
   */
  @Override
  public boolean equals(Object obj) {
    return this == obj || (
            obj != null
                    && getClass() == obj.getClass()
                    && identity.equals(((UpstartModule) obj).identity)
    );
  }

  @Override
  public int hashCode() {
    return 31 * identity.hashCode() + getClass().hashCode();
  }
}
