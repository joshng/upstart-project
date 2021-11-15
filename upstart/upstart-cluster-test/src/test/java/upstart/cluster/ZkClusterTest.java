package upstart.cluster;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import upstart.ExecutorServiceScheduler;
import upstart.services.UpstartService;
import upstart.cluster.test.UpstartClusterTest;
import upstart.cluster.test.UpstartTestClusterBuilder;
import upstart.cluster.test.ZookeeperFixture;
import upstart.cluster.zk.ClusterId;
import upstart.cluster.zk.ZkClusterModule;
import upstart.cluster.zk.ZkResourceLocker;
import upstart.config.UpstartModule;
import upstart.services.IdleService;
import upstart.util.exceptions.MultiException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.async.api.AsyncCuratorFrameworkDsl;
import org.apache.zookeeper.CreateMode;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;

@UpstartClusterTest(nodeCount = 1)
class ZkClusterTest extends UpstartModule {
  private static final Duration CONVERGENCE_TIMEOUT = Duration.ofSeconds(5);

  @Override
  protected void configure() {
    install(ClusterModule.class);
    install(ZkClusterModule.class);
    install(ExecutorServiceScheduler.Module.class);
    install(UpstartClusterNodeModule.class);
  }

  @Test
  void cycle(UpstartTestClusterBuilder clusterBuilder, ZookeeperFixture zk) throws Exception {
    PartitionId partition0 = PartitionId.of(0);
    UpstartService app = clusterBuilder.getNode(0);

    PartitionAssignmentCoordinator blockedCoordinator = app.getInstance(PartitionAssignmentCoordinator.class);

    // given: a node that is responsible for partition-0
    assertThat(blockedCoordinator.currentAssignment().join()).contains(partition0);

    // when: a new node starts and assumes ownership of partition-0
    UpstartService additionalNode = clusterBuilder.startAdditionalNode(2);
    additionalNode.getStartedFuture().get(5, TimeUnit.SECONDS);

    assertThat(blockedCoordinator.currentAssignment().join()).doesNotContain(partition0);

    FakeResourceLocker resourceLocker = app.getInstance(FakeResourceLocker.class);
    String lockInstancePath = resourceLocker.lockInstancePath(partition0);

    String lockNodePath = lockInstancePath + "/fakeNodeId:";
    try (CuratorFramework curator = zk.newCuratorService()) {
      // when: we obstruct the lock for partition-0
      String fakeLockPath = curator.create().withProtection().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(lockNodePath);

      // when: we stop the node that was holding partition-0
      additionalNode.stop().get(5, TimeUnit.SECONDS);

      // when: the original node now attempts to re-establish ownership of partition-0, but cannot, because of the fake lock
      await().atMost(CONVERGENCE_TIMEOUT).untilAsserted(() -> {
        assertThat(blockedCoordinator.currentAssignment().join()).contains(partition0);
        List<ClusterNodeId> lockNodes = resourceLocker.fetchLockStatus().join().get(partition0.toString());
        assertThat(lockNodes).hasSize(2);
        assertThat(lockNodes.get(0)).isEqualTo(ClusterNodeId.of("fakeNodeId"));
        assertThat(lockNodes.get(1)).isEqualTo(ClusterNodeId.of("node-00"));
      });

      // when: another node starts and reclaims partition-0
      UpstartService nextNode = clusterBuilder.startAdditionalNode(2);
      await().atMost(CONVERGENCE_TIMEOUT).untilAsserted(() -> {
        assertThat(blockedCoordinator.currentAssignment().join()).doesNotContain(partition0);
        List<ClusterNodeId> lockNodes = resourceLocker.fetchLockStatus().join().get(partition0.toString());
        assertThat(lockNodes).hasSize(2);
        assertThat(lockNodes.get(1)).isEqualTo(ClusterNodeId.of("node-02"));
      });

      // then: the new node also cannot obtain the lock
      assertThat(additionalNode.isRunning()).isFalse();

      // when: we now release the fake lock
      curator.delete().forPath(fakeLockPath);

      await().atMost(CONVERGENCE_TIMEOUT).untilAsserted(() -> {
        assertThat(nextNode.isRunning()).isTrue();
      });

      // then: the new node can successfully obtain the lock

      PARTITION_EXCEPTIONS.get().throwRuntimeIfAny();
    }
  }

  public static class ClusterModule extends PartitionCoordinationModule<ZkClusterTest.FakePartition> {
    @Override
    protected int partitionCount() {
      return 1;
    }

    @Override
    protected Class<FakePartition> partitionServiceClass() {
      return FakePartition.class;
    }

    @Override
    protected Module partitionServiceModule() {
      return Modules.EMPTY_MODULE;
    }

    @Override
    protected Class<? extends DistributedResourceLocker> partitionResourceLockerClass() {
      return FakeResourceLocker.class;
    }

    @Override
    protected void configure() {
      super.configure();
      bind(ClusterId.class).toInstance(ClusterId.of("test-cluster"));
    }
  }

  static class FakeResourceLocker extends ZkResourceLocker<PartitionId> {

    @Inject
    public FakeResourceLocker(ClusterNodeId nodeId, AsyncCuratorFrameworkDsl framework) {
      super("/fakeLocks", PartitionId::toString, nodeId, framework);
    }
  }

  private static AtomicReference<MultiException> PARTITION_EXCEPTIONS = new AtomicReference<>(MultiException.Empty);

  static class FakePartition extends IdleService {
    static final Map<PartitionId, FakePartition> LIVE_INSTANCES = new ConcurrentHashMap<>();
    private final PartitionId partitionId;

    @Inject
    public FakePartition(PartitionId partitionId) {
      addListener(new Listener() {
        @Override
        public void failed(State from, Throwable failure) {
          PARTITION_EXCEPTIONS.updateAndGet(x -> x.with(failure));
        }
      }, MoreExecutors.directExecutor());
      this.partitionId = partitionId;
    }

    @Override
    protected void startUp() throws Exception {
      checkState(LIVE_INSTANCES.putIfAbsent(partitionId, this) == null, "Another FakePartition was running!");
    }

    @Override
    protected void shutDown() throws Exception {
      checkState(LIVE_INSTANCES.remove(partitionId, this), "FakePartition shutdown when not started!");
    }
  }
}
