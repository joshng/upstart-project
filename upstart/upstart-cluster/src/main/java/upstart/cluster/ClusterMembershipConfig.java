package upstart.cluster;

import upstart.config.annotations.ConfigPath;

import java.time.Duration;

@ConfigPath("upstart.cluster.membership")
public interface ClusterMembershipConfig {
  /**
   * The time to wait after each membership change to allow subsequent changes to coalesce
   * @return
   */
  Duration idleTransitionTimeout();

  /**
   * The maximum time we'll let membership transitions coalesce before applying them
   */
  Duration maxTransitionDelay();
}
