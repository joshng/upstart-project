package upstart.metrics.annotations;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import upstart.metrics.TaggedMetricRegistry;
import upstart.MethodInterceptorFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.reflect.Method;

public class MeteredInterceptorFactory implements MethodInterceptorFactory {

  private final AnnotatedMetricNamer namer;
  private final MetricRegistry metricRegistry;

  @Inject
  public MeteredInterceptorFactory(AnnotatedMetricNamer namer, TaggedMetricRegistry metricRegistry) {
    this.namer = namer;
    this.metricRegistry = metricRegistry;
  }

  @Override
  public MethodInterceptor buildInterceptor(Class<?> interceptedClass, Method interceptedMethod) {
    String name = namer.buildMetricName(interceptedClass, interceptedMethod, Metered.class, Metered::value);
    return new MeteredMethodInterceptor(metricRegistry.meter(name));
  }

  public static class MeteredMethodInterceptor implements MethodInterceptor {
    private final Meter meter;

    public MeteredMethodInterceptor(Meter meter) {
      this.meter = meter;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      meter.mark();
      return invocation.proceed();
    }
  }
}
