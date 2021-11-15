package upstart.metrics.annotations;

import com.google.inject.Module;
import com.google.inject.matcher.Matchers;
import upstart.config.UpstartModule;
import org.kohsuke.MetaInfServices;

@MetaInfServices(Module.class)
public class MetricAnnotationsModule extends UpstartModule {
  @Override
  protected void configure() {
    bindInterceptorFactory(Matchers.annotatedWith(Metered.class), MeteredInterceptorFactory.class);
    bindInterceptorFactory(Matchers.annotatedWith(Timed.class), TimedInterceptorFactory.class);
  }
}
