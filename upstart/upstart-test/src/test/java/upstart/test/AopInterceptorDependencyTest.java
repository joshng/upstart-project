package upstart.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.matcher.Matchers;
import upstart.MethodInterceptorFactory;
import upstart.config.UpstartModule;
import upstart.services.NotifyingService;
import upstart.services.ServiceDependencyChecker;
import upstart.util.concurrent.CompletableFutures;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@UpstartServiceTest
public class AopInterceptorDependencyTest extends UpstartModule {
  @Override
  protected void configure() {
    serviceManager().manage(AspectHandlerService.class)
            .manage(InterceptedAspectService.class);
    bindInterceptorFactory(Matchers.annotatedWith(FakeIntercepted.class), FakeInterceptorFactory.class);
  }

  @Inject ServiceDependencyChecker serviceGraph;
  @Inject AspectHandlerService handlerService;
  @Inject InterceptedAspectService serviceWithAspect;

  @AfterInjection
  void checkLifecycle() {
    handlerService.addListener(new Service.Listener() {
      @Override
      public void starting() {
        handlerService.failWithAssertion(() -> assertThat(serviceWithAspect.state()).isEqualTo(Service.State.NEW));
      }
    }, MoreExecutors.directExecutor());

    serviceWithAspect.addListener(new Service.Listener() {
      @Override
      public void stopping(Service.State from) {
        handlerService.failWithAssertion(() -> assertWithMessage("handlerService should stop after serviceWithAspect")
                .that(handlerService.state())
                .isEqualTo(Service.State.RUNNING)
        );
      }
    }, MoreExecutors.directExecutor());
  }

  @Test
  void testInterceptorDependency() {
    serviceGraph.assertThat(serviceWithAspect).dependsUpon(handlerService);
    serviceWithAspect.invokeAspect();
    assertThat(handlerService.invocations).isEqualTo(1);
  }

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface FakeIntercepted {

  }

  @Singleton
  public static class AspectHandlerService extends NotifyingService {

    int invocations;

    @Override
    protected void doStart() {
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }

    void failWithAssertion(Runnable runnable) {
      failWith(CompletableFutures.runSafely(runnable));
    }
  }

  @Singleton
  public static class InterceptedAspectService extends NotifyingService {

    @FakeIntercepted
    public void invokeAspect() {

    }

    @Override
    protected void doStart() {
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }


  }

  private static class FakeInterceptorFactory implements MethodInterceptorFactory, MethodInterceptor {
    private final AspectHandlerService dep;

    @Inject
    private FakeInterceptorFactory(AspectHandlerService dep) {
      this.dep = dep;
    }

    @Override
    public MethodInterceptor buildInterceptor(Class<?> interceptedClass, Method interceptedMethod) {
      return this;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      dep.invocations++;
      return invocation.proceed();
    }
  }
}
