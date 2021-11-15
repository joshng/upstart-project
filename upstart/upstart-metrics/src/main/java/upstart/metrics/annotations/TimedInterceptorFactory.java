package upstart.metrics.annotations;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import upstart.metrics.TaggedMetricRegistry;
import upstart.MethodInterceptorFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.reflect.Method;

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
    return new TimedMethodInterceptor(metricRegistry.timer(name));
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
}
