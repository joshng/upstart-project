package upstart.metrics.annotations;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import upstart.metrics.TaggedMetricRegistry;
import upstart.MethodInterceptorFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

import static com.google.common.base.Preconditions.checkState;

public class TimedInterceptorFactory implements MethodInterceptorFactory {

  private final AnnotatedMetricNamer namer;
  private final MetricRegistry metricRegistry;

  @Inject
  public TimedInterceptorFactory(AnnotatedMetricNamer namer, TaggedMetricRegistry metricRegistry) {
    this.namer = namer;
    this.metricRegistry = metricRegistry;
  }

  @Override
  public MethodInterceptor buildInterceptor(Class<?> interceptedClass, Method interceptedMethod) {
    String name = namer.buildMetricName(interceptedClass, interceptedMethod, Timed.class, Timed::value);
    Timer timer = metricRegistry.timer(name);
    if (interceptedMethod.getAnnotation(Timed.class).async()) {
      checkState(
              CompletionStage.class.isAssignableFrom(interceptedMethod.getReturnType()),
              "Timed(async) methods must return CompletionStage<?>: %s", interceptedMethod
      );
      return new AsyncTimedMethodInterceptor(timer);
    } else {
      return new TimedMethodInterceptor(timer);
    }
  }


  public static class TimedMethodInterceptor implements MethodInterceptor {
    private final Timer timer;

    public TimedMethodInterceptor(Timer timer) {
      this.timer = timer;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      try (Timer.Context ignored = timer.time()) {
        return invocation.proceed();
      }
    }
  }

  public static class AsyncTimedMethodInterceptor implements MethodInterceptor {
    private final Timer timer;

    public AsyncTimedMethodInterceptor(Timer timer) {
      this.timer = timer;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      Timer.Context context = timer.time();
      try {
        CompletionStage<?> result = (CompletionStage<?>) invocation.proceed();
        return result.whenComplete((ignored, e) -> context.stop());
      } catch (Throwable e) {
        context.close();
        throw e;
      }
    }
  }
}
