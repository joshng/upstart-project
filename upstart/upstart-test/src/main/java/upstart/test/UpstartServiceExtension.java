package upstart.test;

import com.google.common.collect.Lists;
import upstart.InternalTestBuilder;
import upstart.UpstartApplication;
import upstart.services.ManagedServiceGraph;
import upstart.services.ManagedServicesModule;
import upstart.util.reflect.MultiMethodInvoker;
import upstart.util.reflect.Reflect;
import upstart.util.concurrent.Threads;
import upstart.util.exceptions.ThrowingConsumer;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.ToIntFunction;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

public class UpstartServiceExtension implements BeforeEachCallback, AfterTestExecutionCallback {
  public static final long DEFAULT_TIMEOUT_NANOS = UpstartServiceTest.DEFAULT_TIMEUNIT
          .toNanos(UpstartServiceTest.DEFAULT_TIMEOUT);
  public static final MultiMethodInvoker.Dispatcher<Object> AFTER_SERVICE_DISPATCHER =
          MultiMethodInvoker.machingMethodDispatcher(Reflect.annotationPredicate(AfterServiceStarted.class));
  public static final MultiMethodInvoker.Dispatcher<Object> BEFORE_SERVICE_STOP_DISPATCHER =
          MultiMethodInvoker.machingMethodDispatcher(Reflect.annotationPredicate(BeforeServiceStopped.class));

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    Optional<UpstartServiceTest> annotation = ExtensionContexts.findNearestAnnotation(UpstartServiceTest.class, context);
    long timeoutNanos = timeoutNanos(annotation, UpstartServiceTest::serviceStartupTimeout);
    UpstartTestBuilder testBuilder = InternalTestBuilder.getInstance(context);
    annotation.map(UpstartServiceTest::value)
            .filter(not(isEqual(UpstartApplication.class)))
            .ifPresent(testBuilder::installModule);

    withServiceGraph(testBuilder, graph -> {
      try {
        graph.start().get(timeoutNanos, TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        throw new RuntimeException("Timed out waiting to start:\n" + graph, e);
      }
      ExtensionContexts.allNestedTestInstances(context)
              .forEach(instance -> AFTER_SERVICE_DISPATCHER.dispatch(instance, false));
    });
  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    long timeoutNanos = timeoutNanos(
            ExtensionContexts.findNearestAnnotation(UpstartServiceTest.class, context),
            UpstartServiceTest::serviceShutdownTimeout
    );
    withServiceGraph(InternalTestBuilder.getInstance(context), graph -> {
      Lists.reverse(ExtensionContexts.allNestedTestInstances(context))
              .forEach(instance -> BEFORE_SERVICE_STOP_DISPATCHER.dispatch(instance, false));
      try {
        graph.stop().get(timeoutNanos, TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        throw new TimeoutException("Timed out waiting for shutdown.\n" + graph + "\n\nThread dump:\n" + Threads.formatThreadDump());
      }
    });
  }

  private void withServiceGraph(UpstartTestBuilder testBuilder, ThrowingConsumer<ManagedServiceGraph> consumer) {
    testBuilder.withInjector(
            injector -> consumer.acceptOrThrow(injector.getInstance(ManagedServicesModule.INFRASTRUCTURE_GRAPH_KEY))
    );
  }

  private static long timeoutNanos(Optional<UpstartServiceTest> annotation, ToIntFunction<UpstartServiceTest> timeoutParam) {
    return annotation
            .map(anno -> anno.timeoutUnit().toNanos(timeoutParam.applyAsInt(anno)))
            .orElse(DEFAULT_TIMEOUT_NANOS);
  }
}
