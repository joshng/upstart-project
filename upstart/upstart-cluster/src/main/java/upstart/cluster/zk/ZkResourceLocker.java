package upstart.cluster.zk;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import upstart.cluster.ClusterNodeId;
import upstart.cluster.DistributedResourceLocker;
import upstart.cluster.FenceToken;
import upstart.util.collect.Pair;
import upstart.util.concurrent.CompletableFutures;
import org.apache.curator.utils.ZKPaths;
import org.apache.curator.x.async.AsyncStage;
import org.apache.curator.x.async.api.AsyncCuratorFrameworkDsl;
import org.apache.curator.x.async.api.CreateOption;
import org.apache.curator.x.async.api.DeleteOption;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

public class ZkResourceLocker<ResourceId> extends DistributedResourceLocker<ResourceId> {
  private static final Set<CreateOption> CREATE_PARENTS_PROTECTED = EnumSet.of(CreateOption.createParentsIfNeeded, CreateOption.doProtected);
  private static final Set<DeleteOption> DELETE_QUIETLY_GUARANTEED = EnumSet.of(DeleteOption.quietly, DeleteOption.guaranteed);
  private static final String OWNER_ID_TERMINATOR = ":";
  public static final int CURATOR_PROTECTION_PREFIX_LEN = 40; // _c_[36-char-UUID]-
  private final String rootPath;
  private final Function<ResourceId, String> resourceFormatter;
  private final AsyncCuratorFrameworkDsl framework;

  public ZkResourceLocker(String rootPath, Function<ResourceId, String> resourceFormatter, ClusterNodeId nodeId, AsyncCuratorFrameworkDsl framework) {
    super(nodeId);
    this.rootPath = rootPath;
    this.resourceFormatter = resourceFormatter;
    this.framework = framework;
  }

  @Override
  protected Strategy leaseStrategy(ResourceId resource, ClusterNodeId nodeId) {
    return new LeaseStrategy(lockInstancePath(resource), nodeId.sessionId());
  }

  public CompletableFuture<ListMultimap<String, ClusterNodeId>> fetchLockStatus() {
    return getAllLockPaths().thenCompose(lockPaths ->
            CompletableFutures.allAsList(lockPaths.stream()
                    .map(lockPath -> fetchLocksAtPath(lockPath)
                            .thenApply(paths -> Pair.of(lockPath, paths)).toCompletableFuture())
            ).thenApply(pairList -> pairList.stream()
                    .collect(Multimaps.flatteningToMultimap(
                            Pair::getKey, pair -> pair.getValue().stream(),
                            MultimapBuilder.hashKeys().arrayListValues()::build)))
    ).toCompletableFuture();
  }

  public CompletionStage<List<ClusterNodeId>> fetchLockQueue(ResourceId resource) {
    return fetchLocksAtPath(lockInstancePath(resource));
  }

  private CompletionStage<List<ClusterNodeId>> fetchLocksAtPath(String lockPath) {
    return framework.getChildren().forPath(ZKPaths.makePath(rootPath, lockPath))
            .thenApply(candidates -> candidates.stream()
                    .sorted(Comparator.comparing(ZkResourceLocker::extractZkSequence))
                    .map(ZkResourceLocker::extractClusterNodeId)
                    .collect(Collectors.toList())
            );
  }

  protected CompletionStage<List<String>> getAllLockPaths() {
    return framework.getChildren().forPath(rootPath);
  }


  @VisibleForTesting
  public String lockInstancePath(ResourceId resource) {
    return ZKPaths.makePath(rootPath, resourceFormatter.apply(resource));
  }

  protected class LeaseStrategy implements Strategy {
    private final String lockPath;
    private final String ownerId;
    private AsyncStage<String> lockNodePathFuture;
    private CompletionStage<Stat> filledStatFuture;
    private volatile boolean released = false;

    LeaseStrategy(String lockPath, String ownerId) {
      this.lockPath = lockPath;
      this.ownerId = ownerId;
    }

    @Override
    public CompletableFuture<Optional<FenceToken>> requestLock() {
      Stat stat = new Stat();
      lockNodePathFuture = framework.create()
              .withOptions(CREATE_PARENTS_PROTECTED, CreateMode.EPHEMERAL_SEQUENTIAL, null, stat)
              .forPath(lockPath + "/" + ownerId + OWNER_ID_TERMINATOR); // append ':' to avoid seqNo confusion in case ownerId ends with '-'
      filledStatFuture = lockNodePathFuture.thenCompose(path -> stat.getEphemeralOwner() == 0
              ? framework.checkExists().forPath(path)
              : CompletableFuture.completedFuture(stat));
      return lockNodePathFuture
              .thenApply(ZkResourceLocker::extractZkSequence)
              .thenCompose(this::checkLock)
              .toCompletableFuture();
    }

    private CompletionStage<Optional<FenceToken>> checkLock(long seqNo) {
      if (released) {
        return CANCELLED_LOCK_FUTURE;
      }
      AsyncStage<List<String>> children = framework.watched().getChildren().forPath(lockPath);
      return children.thenCompose(childPathList -> {
        if (released) {
          return CANCELLED_LOCK_FUTURE;
        }
        long index = childPathList.stream()
                .mapToLong(ZkResourceLocker::extractZkSequence)
                .filter(n -> seqNo - n >= 0) // use subtraction to handle unsigned wraparound
                .count();
        if (index == 1) {
          // our node is first in line: we have the lock
          return filledStatFuture.thenApply(stat -> Optional.of(FenceToken.of(stat.getCzxid())));
        } else {
          checkState(index > 1, "Lost lock-node!", lockNodePathFuture.toCompletableFuture().join());

          // there are other nodes before us. watch for a change (via children.event), then check again
          return children.event().thenCompose(__ -> checkLock(seqNo));
        }
      });
    }

    @Override
    public CompletableFuture<?> relinquishLock() {
      released = true;
      return lockNodePathFuture.thenCompose(path -> framework.delete().withOptions(DELETE_QUIETLY_GUARANTEED).forPath(path))
              .toCompletableFuture();
    }
  }

  public static int extractZkSequence(String sequenceNodePath) {
    int pathLen = sequenceNodePath.length();
    boolean isNegative = sequenceNodePath.charAt(pathLen - 11) == '-';
    return Integer.parseInt(sequenceNodePath.substring(pathLen - (isNegative ? 11 : 10), pathLen));
  }

  public static ClusterNodeId extractClusterNodeId(String sequenceNodePath) {
    return ClusterNodeId.of(sequenceNodePath.substring(sequenceNodePath.lastIndexOf('/') + CURATOR_PROTECTION_PREFIX_LEN + 1, sequenceNodePath.lastIndexOf(OWNER_ID_TERMINATOR)));
  }
}

