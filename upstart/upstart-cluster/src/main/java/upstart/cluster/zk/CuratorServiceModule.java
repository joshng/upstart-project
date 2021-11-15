package upstart.cluster.zk;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.OptionalBinder;
import upstart.config.UpstartModule;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.DefaultZookeeperFactory;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.apache.curator.x.async.api.AsyncCuratorFrameworkDsl;

public class CuratorServiceModule extends UpstartModule {
  @Override
  public void configure() {
    OptionalBinder.newOptionalBinder(binder(), ZookeeperFactory.class).setDefault().to(DefaultZookeeperFactory.class);
    serviceManager().manage(CuratorService.class);
    bindConfig(ZkConfig.class);
    bind(AsyncCuratorFrameworkDsl.class).to(AsyncCuratorFramework.class);
  }

  public static LinkedBindingBuilder<ZookeeperFactory> bindZookeeperFactory(Binder binder) {
    return OptionalBinder.newOptionalBinder(binder, ZookeeperFactory.class).setBinding();
  }

  @Provides
  CuratorFramework syncFramework(CuratorService service) {
    return service.getFramework();
  }

  @Provides
  AsyncCuratorFramework asyncFramework(CuratorService service) {
    return service.getAsyncFramework();
  }
}
