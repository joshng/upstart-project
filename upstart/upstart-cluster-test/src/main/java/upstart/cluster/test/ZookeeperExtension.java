package upstart.cluster.test;

import upstart.test.UpstartExtension;
import upstart.test.SingletonParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ZookeeperExtension extends SingletonParameterResolver<ZookeeperFixture> implements BeforeEachCallback, AfterEachCallback {
    public ZookeeperExtension() {
        super(ZookeeperFixture.class);
    }

    @Override
    protected ZookeeperFixture createContext(ExtensionContext extensionContext) throws Exception {
        return new ZookeeperFixture();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        ZookeeperFixture fixture = getOrCreateContext(context);
        fixture.start();
        UpstartExtension.getOptionalTestBuilder(context).ifPresent(fixture::setupUpstartClusterConfig);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        getExistingContext(context).ifPresent(ZookeeperFixture::stop);
    }
}
