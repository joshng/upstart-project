package upstart.aws;

import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.NamedThreadFactory;
import upstart.util.concurrent.services.ThreadPoolService;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class AwsCallbackThreadPool extends ThreadPoolService {
  AwsCallbackThreadPool() {
    super(Duration.ofSeconds(1));
  }

  @Override
  protected ExecutorService buildExecutorService() {
    // TODO: make this more configurable via a generic ThreadPoolConfig facility
    return Executors.newCachedThreadPool(new NamedThreadFactory("aws-cb"));
  }
}
