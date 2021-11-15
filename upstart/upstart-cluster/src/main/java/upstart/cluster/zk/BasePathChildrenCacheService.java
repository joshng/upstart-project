package upstart.cluster.zk;

import upstart.services.BaseComposableService;
import upstart.services.IdleService;
import upstart.services.ManagedServiceGraph;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;

import java.util.List;

/**
 * A simple wrapper around a curator {@link PathChildrenCache} to decorate it with a
 * {@link BaseComposableService} for compatibility with lifecycle-management via {@link ManagedServiceGraph}.
 */
public abstract class BasePathChildrenCacheService extends IdleService {
    private PathChildrenCache pathChildrenCache;
    private final CuratorFramework curator;

    protected BasePathChildrenCacheService(CuratorFramework curator) {
        this.curator = curator;
    }

    protected abstract String rootPath();

    @Override
    protected void startUp() throws Exception {
        pathChildrenCache = buildCache(curator);
        pathChildrenCache.getListenable().addListener(listener());
        pathChildrenCache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
    }

    protected PathChildrenCache buildCache(CuratorFramework curator) {
        return new PathChildrenCache(curator, rootPath(), true) {
            @Override
            protected void handleException(Throwable e) {
                notifyFailed(e);
            }
        };
    }


    protected abstract PathChildrenCacheListener listener();

    @Override
    protected void shutDown() throws Exception {
        pathChildrenCache.close();
    }

    /**
     * A utility-class for handling {@link PathChildrenCacheEvent}s
     */
    protected abstract static class ChildrenListener implements PathChildrenCacheListener {
        private boolean initialized = false;

        @Override
        public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
            switch (event.getType()) {

                case CHILD_ADDED:
                    if (initialized) onChildAdded(event.getData());
                    break;
                case CHILD_UPDATED:
                    onChildUpdated(event.getData());
                    break;
                case CHILD_REMOVED:
                    onChildRemoved(event.getData());
                    break;
                case CONNECTION_SUSPENDED:
                    onConnectionSuspended();
                    break;
                case CONNECTION_RECONNECTED:
                    onConnectionReconnected();
                    break;
                case CONNECTION_LOST:
                    onConnectionLost();
                    break;
                case INITIALIZED:
                    initialized = true;
                    onInitialized(event.getInitialData());
                    break;
            }
        }

        public void onInitialized(List<ChildData> data) {
            for (ChildData datum : data) {
                onChildAdded(datum);
            }
        }

        public abstract void onChildAdded(ChildData childData);
        public abstract void onChildUpdated(ChildData childData);
        public abstract void onChildRemoved(ChildData childData);

        public void onConnectionSuspended() {

        }

        public void onConnectionReconnected() {

        }

        public abstract void onConnectionLost();
    }


}
