package upstart.b4.devops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import upstart.b4.B4Function;
import upstart.b4.B4TargetContext;
import upstart.commandExecutor.CommandPolicy;
import upstart.commandExecutor.CommandResult;
import upstart.util.exceptions.UncheckedIO;
import upstart.util.concurrent.Throttler;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobCondition;
import io.fabric8.kubernetes.api.model.batch.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.immutables.value.Value;

import javax.inject.Inject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KubeApplyFunction implements B4Function<KubeApplyFunction.KubeApplyConfig> {
  private static final Duration READINESS_POLL_INTERVAL = Duration.ofSeconds(1);
  private static final Duration READINESS_REPORT_INTERVAL = Duration.ofSeconds(10);
  private final KubernetesClient kc;
  private final ObjectMapper objectMapper;

  @Inject
  public KubeApplyFunction(KubernetesClient kc, ObjectMapper objectMapper) {
    this.kc = kc;
    this.objectMapper = objectMapper;
  }

  @Override
  public void clean(KubeApplyConfig config, B4TargetContext context) throws Exception {
    config.deleteResources(context);
  }

  @Override
  public void run(KubeApplyConfig config, B4TargetContext context) throws Exception {
    if (!context.activePhases().doClean) clean(config, context);

    abstract class ReadinessChecker implements BooleanSupplier {
      private final Throttler statusThrottler = new Throttler(READINESS_REPORT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
      private String prevStatusDescription = null;

      abstract boolean isReady();

      abstract String describeStatus();

      @Override
      public boolean getAsBoolean() {
        if (isReady()) return true;

        if (statusThrottler.mayBeAvailable()) {
          String status = describeStatus();
          if (!status.equals(prevStatusDescription) && statusThrottler.tryAcquire()) {
            context.say(status);
            prevStatusDescription = status;
          }
        }
        return false;
      }
    }

    class PermanentFailureException extends RuntimeException {
      PermanentFailureException(String message) {
        super(message);
      }
    }

    class StatefulSetReadiness extends ReadinessChecker {
      private final LabelSelector labelSelector;
      private final String name;

      private StatefulSetReadiness(KubeApplyConfig.ResourceSelector selector) {
        this.name = selector.name();
        labelSelector = new LabelSelector(ImmutableList.of(), selector.podSelector());
      }

      @Override
      boolean isReady() {
        StatefulSet statefulSet = kc.apps()
                .statefulSets()
                .inNamespace(config.namespace())
                .withName(name)
                .get();

        StatefulSetStatus status = statefulSet.getStatus();

        Integer expectedReplicas = status.getReplicas();
        // we use Integer.equals because these values are initially null
        return expectedReplicas.equals(status.getUpdatedReplicas()) && expectedReplicas.equals(status.getReadyReplicas());
      }

      @Override
      String describeStatus() {
        List<Pod> pods = kc.pods().inNamespace(config.namespace()).withLabelSelector(labelSelector).list().getItems();
        String description = pods.stream().map(Pod::getStatus)
                .flatMap(pod -> pod.getContainerStatuses().stream())
                .map(cs -> UncheckedIO.getUnchecked(() -> objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(cs)))
                .collect(Collectors.joining("\n"));

        return String.format("StatefulSet %s: waiting for containers:\n%s", name, description);
      }
    }

    class DeploymentReadiness extends ReadinessChecker {
      private final LabelSelector labelSelector;
      private final String name;

      DeploymentReadiness(KubeApplyConfig.ResourceSelector selector) {
        this.name = selector.name();
        labelSelector = new LabelSelector(ImmutableList.of(), selector.podSelector());
      }

      @Override
      boolean isReady() {
        List<Deployment> deployments = kc.apps().deployments()
                .inNamespace(config.namespace())
                .withLabelSelector(labelSelector)
                .list()
                .getItems();
        if(deployments.isEmpty()) return false;
        Deployment deployment = deployments.get(0);
        DeploymentStatus status = deployment.getStatus();
        return status.getReadyReplicas() != null && status.getAvailableReplicas() > 0;
      }

      @Override
      String describeStatus() {
        List<Pod> pods = kc.pods()
                .inNamespace(config.namespace())
                .withLabelSelector(labelSelector)
                .list()
                .getItems();
        String description = pods.stream().map(Pod::getStatus)
                .flatMap(pod -> pod.getContainerStatuses().stream())
                .map(cs -> UncheckedIO.getUnchecked(() -> objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(cs)))
                .collect(Collectors.joining("\n"));
        return String.format("Deployment %s: waiting for container:\n%s", name, description);
      }
    }



    class JobReadiness extends ReadinessChecker {
      private final LabelSelector labelSelector;
      private final String name;

      JobReadiness(KubeApplyConfig.ResourceSelector selector) {
        this.name = selector.name();
        labelSelector = new LabelSelector(ImmutableList.of(), selector.podSelector());
      }

      @Override
      boolean isReady() {
        Job job = kc.batch().jobs()
                .inNamespace(config.namespace())
                .withName(name)
                .get();

        JobStatus status = job.getStatus();
        List<JobCondition> conditions = status.getConditions();
        long terminalFailures = conditions.stream()
                .filter(condition -> condition.getType().equals("Failed"))
                .filter(condition -> Boolean.parseBoolean(condition.getStatus()))
                .count();

        if (terminalFailures > 0) {
          throw new PermanentFailureException("Job " + name + " has failed.");
        }
        return status.getSucceeded() != null && status.getSucceeded() > 0;
      }

      @Override
      String describeStatus() {
        List<Pod> pods = kc.pods().inNamespace(config.namespace()).withLabelSelector(labelSelector).list().getItems();
        String description = pods.stream().map(Pod::getStatus)
                .flatMap(pod -> pod.getContainerStatuses().stream())
                .map(cs -> UncheckedIO.getUnchecked(() -> objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(cs)))
                .collect(Collectors.joining("\n"));
        return String.format("Job %s: waiting for container:\n%s", name, description);
      }
    }

    context.run(config.kubectlExecutable(), "apply", "--force", "-f", config.spec());

    List<BooleanSupplier> readinessTests = Streams.concat(
              Streams.stream(config.deployment().map(p -> new DeploymentReadiness(p))),
              Streams.stream(config.statefulSet().map(ss -> new StatefulSetReadiness(ss))),
            Streams.stream(config.job().map(j -> new JobReadiness(j)))
    ).collect(Collectors.toList());

    RetryPolicy<Boolean> kubeRetryPolicy = new RetryPolicy<Boolean>()
            .withMaxAttempts(15)
            .handleIf(t -> !(t instanceof PermanentFailureException))
            .onFailedAttempt(t -> context.sayFormatted("Could not perform readiness check: %s", t.getLastFailure().getMessage()))
            .withBackoff(1, 60, ChronoUnit.SECONDS);

    while (!readinessTests.isEmpty()) {
      context.sleepOrCancel(READINESS_POLL_INTERVAL);
      readinessTests.removeIf(b -> Failsafe.with(kubeRetryPolicy).get(b::getAsBoolean));
    }
  }

  @Override
  public void cancel() {
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableKubeApplyConfig.class)
  public interface KubeApplyConfig {
    String namespace();
    String kubectlExecutable();
    String spec();
    Optional<ResourceSelector> statefulSet();
    Optional<ResourceSelector> job();
    Optional<ResourceSelector> deployment();

    default Stream<String> allPodResources() {
      return Streams.concat(
                Streams.stream(deployment().map(pod -> "deployment/" + pod.name())),
                Streams.stream(statefulSet().map(ss -> "statefulset/" + ss.name())),
              Streams.stream(job().map(job -> "job/" + job.name()))
      );
    }
    default void deleteResources(B4TargetContext context) {
      allPodResources().forEach(pod -> deleteResource(pod, context));
    }

    default void deleteResource(String resource, B4TargetContext context) {
      CommandResult.Completed deletionResult = context.run(kubectlExecutable(),
              cmd -> cmd.addArgs("-n", namespace(), "--force", "--grace-period=0", "delete", resource)
                      .policy(CommandPolicy.RequireCompleted)
      );
      if (deletionResult.exitCode() != 0) {
        if (context.requestedPhases().isPresent() && context.activePhases().doClean) {
          context.sayFormatted("Warning: unable to delete %s\n%s", resource, deletionResult.description());
        }
      } else {
        context.say("DONE: deleted", resource);
      }
    }

    @Value.Immutable
    @JsonDeserialize(as = ImmutableResourceSelector.class)
    interface ResourceSelector {
      static ImmutableResourceSelector.Builder builder() {
        return ImmutableResourceSelector.builder();
      }

      String name();

      @Value.Default
      default Map<String, String> podSelector() {
        return ImmutableMap.of("app", name());
      }
    }
  }
}
