package upstart.cluster.zk;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.primitives.Ints;
import upstart.cluster.ClusterAdministrator;
import upstart.util.concurrent.CompletableFutures;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.apache.curator.x.async.api.AsyncCuratorFrameworkDsl;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A program that triggers a cluster-wide shutdown
 */
public class ZkClusterAdministrator implements ClusterAdministrator {
  private static final Logger LOG = LoggerFactory.getLogger(ZkClusterAdministrator.class);
  private final AsyncCuratorFrameworkDsl asyncFramework;

  @Inject
  public ZkClusterAdministrator(AsyncCuratorFrameworkDsl asyncFramework) {
    this.asyncFramework = asyncFramework;
  }

  @Override
  public CompletableFuture<?> requestClusterShutdown(ClusterId clusterId) {
    LOG.info("Requesting cluster shutdown...");
    String epochPath = ZkClusterMembership.getEpochPath(clusterId);
    return asyncFramework.checkExists().forPath(epochPath).thenCompose(stat -> {
      if (stat != null) {
        return CompletableFutures.recover(asyncFramework.setData().withVersion(stat.getVersion()).forPath(epochPath, Ints.toByteArray(stat.getVersion() + 1)),
                KeeperException.BadVersionException.class,
                e -> {
                  LOG.info("Lost a race to increment the cluster-epoch; someone else must have already shut down the cluster?");
                  return null;
                });

      } else {
        LOG.warn("Requested shutdown for cluster when cluster-epoch was not defined");
        return CompletableFutures.nullFuture();
      }
    }).toCompletableFuture();
  }

  public static void main(String[] argv) {
    Args args = new Args();
    JCommander commander = JCommander.newBuilder()
            .addObject(args)
            .addCommand("shutdown", new Args.Shutdown())
            .programName(ZkClusterAdministrator.class.getSimpleName())
            .args(argv)
            .build();

    if (args.help) {
      commander.usage();
      System.exit(0);
    }

    String parsedCommand = commander.getParsedCommand();
    if (parsedCommand == null) {
      commander.usage();
      System.exit(1);
    }
    checkArgument("shutdown".equals(parsedCommand), "Unrecognized command", parsedCommand);

    LOG.info("Requesting cluster shutdown...");
    try (CuratorFramework client = CuratorFrameworkFactory.newClient(args.connectString, (retryCount, elapsedTimeMs, sleeper) -> false)) {
      client.start();

      new ZkClusterAdministrator(AsyncCuratorFramework.wrap(client))
              .requestClusterShutdown(ClusterId.of(args.clusterId))
              .join();
      LOG.info("Shutdown initiated.");
    }
  }

  private static class Args {
    @Parameter(names = "-z", required = true, description = "Zookeeper connection string (required)\n")
    String connectString;

    @Parameter(names = "-c", required = true, description = "Upstart Cluster-id (required)\n")
    String clusterId;

    @Parameter(names = {"--help", "-h"}, help = true)
    boolean help;

    @Parameters(commandNames = "shutdown", commandDescription = "Trigger  cluster shutdown")
    static class Shutdown {

    }
  }
}
