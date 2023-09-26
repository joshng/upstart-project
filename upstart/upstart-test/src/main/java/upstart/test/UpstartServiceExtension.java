package upstart.test;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Service;
import upstart.InternalTestBuilder;
import upstart.UpstartApplication;
import upstart.UpstartService;
import upstart.managedservices.ManagedServiceGraph;
import upstart.managedservices.ManagedServicesModule;
import upstart.test.truth.CompletableFutureSubject;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.Threads;
import upstart.util.reflect.MultiMethodInvoker;
import upstart.util.reflect.Reflect;
import upstart.util.exceptions.ThrowingConsumer;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.ToIntFunction;

import static com.google.common.truth.Truth.assertWithMessage;
import static upstart.util.functions.MoreFunctions.notEqual;

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
            .filter(notEqual(UpstartApplication.class))
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
    Optional<UpstartServiceTest> annotation = ExtensionContexts.findNearestAnnotation(UpstartServiceTest.class, context);
    long timeoutNanos = timeoutNanos(annotation, UpstartServiceTest::serviceShutdownTimeout);
    var testBuilder = InternalTestBuilder.getInstance(context);
    withServiceGraph(testBuilder, graph -> {
      Lists.reverse(ExtensionContexts.allNestedTestInstances(context))
              .forEach(instance -> BEFORE_SERVICE_STOP_DISPATCHER.dispatch(instance, false));
      var shutdownFuture = graph.stop();
      CompletableFutureSubject<Service.State> shutdownAssertion;
      try {
        shutdownAssertion = assertWithMessage("Service shutdown")
                .about(CompletableFutureSubject.<Service.State>completableFutures())
                .that(shutdownFuture)
                .doneWithin(Deadline.within(timeoutNanos, TimeUnit.NANOSECONDS));
      } catch (AssertionError e) {
        AssertionError error = new AssertionError(
                "Timed out waiting to stop:\n" + graph + "\n" + Threads.formatThreadDump(),
                e
        );
        context.getExecutionException().ifPresent(error::addSuppressed);
        throw error;
      }
      testBuilder.shutdownVisitor().ifPresentOrElse(
              expectation -> expectation.accept(shutdownFuture),
              shutdownAssertion::completedNormally
      );
    });
  }

  private void withServiceGraph(UpstartTestBuilder testBuilder, ThrowingConsumer<UpstartService> consumer) {
    testBuilder.withInjector(
            injector -> consumer.acceptOrThrow(injector.getInstance(UpstartService.class))
    );
  }

  private static long timeoutNanos(Optional<UpstartServiceTest> annotation, ToIntFunction<UpstartServiceTest> timeoutParam) {
    return annotation
            .map(anno -> anno.timeoutUnit().toNanos(timeoutParam.applyAsInt(anno)))
            .orElse(DEFAULT_TIMEOUT_NANOS);
  }
}
