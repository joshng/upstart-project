package upstart.config;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.matcher.Matcher;
import upstart.UpstartService;
import upstart.UpstartModuleExtension;
import upstart.managedservices.ManagedServicesModule;
import org.aopalliance.intercept.MethodInterceptor;

import java.lang.reflect.Method;

/**
 * A utility base-class for configuring {@link UpstartService PisqueakApplications}.
 * <p/>
 * The use of this class is strictly optional (but convenient!); the methods defined here all delegate to the
 * {@link UpstartConfigBinder} and {@link ManagedServicesModule#serviceManager}, which can also be invoked directly if necessary.
 */
public abstract class UpstartModule extends AbstractModule implements UpstartModuleExtension {
  /**
   * we override `configure` here just to better surface its IDE autocompletion
   */
  @Override
  protected void configure() {
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
   * Unlike guice's built-in {@link AbstractModule}, UpstartModules are considered equal if they are the same CLASS.
   * This prevents multiple copies of the same type of UpstartModule from being erroneously
   * {@link Binder#install installed} into the same {@link UpstartService} (which can easily happen if
   * a group of Modules form a "diamond" dependency-pattern).
   * <p/>
   * This definition of equality will cause problems for scenarios involving multiple differently-configured copies
   * of the same type of UpstartModule. In such cases, you must override {@link #equals} to determine equality correctly
   * (presumably incorporating the input-parameters or config-values which influence the behavior of the distinct instances).
   */
  @Override
  public boolean equals(Object obj) {
    return this == obj || (obj != null && getClass() == obj.getClass());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
