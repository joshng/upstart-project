package upstart.test;

import upstart.services.ScheduledService;
import upstart.util.reflect.Reflect;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FakeTimeExtension extends SingletonParameterResolver<FakeTime> implements BeforeEachCallback {
  protected FakeTimeExtension() {
    super(FakeTime.class);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    getOrCreateContext(context);
  }

  @Override
  protected FakeTime createContext(ExtensionContext extensionContext) throws Exception {

    List<FakeTimeTest> annotations = ExtensionContexts.findTestAnnotations(FakeTimeTest.class, Reflect.LineageOrder.SubclassBeforeSuperclass, extensionContext)
            .collect(Collectors.toList());
    Instant initialTime = annotations.stream()
            .filter(FakeTimeExtension::hasInitialTime)
            .map(FakeTimeExtension::computeInitialTime)
            .findFirst()
            .orElse(Instant.EPOCH);
    ExecutorService immediateExecutor = annotations.stream()
            .map(FakeTimeTest::immediateExecutorSupplier)
            .filter(cls -> cls != FakeTimeTest.DefaultImmediateExecutorSupplier.class)
            .findFirst()
            .<Supplier<ExecutorService>>map(Reflect::newInstance)
            .orElse(FakeTimeTest.DefaultImmediateExecutorSupplier.INSTANCE)
            .get();

    ZoneId timezone = annotations.stream()
            .map(FakeTimeTest::timezone)
            .filter(z -> !z.isEmpty())
            .findFirst()
            .map(ZoneId::of)
            .orElse(ZoneOffset.UTC);

    Set<Class<? extends ScheduledService>> interceptedSchedules = annotations.stream()
            .flatMap(anno -> Stream.of(anno.interceptSchedules()))
            .collect(Collectors.toSet());

    return FakeTime.install(
            UpstartExtension.getOptionalTestBuilder(extensionContext)
                    .orElseThrow(() -> new IllegalStateException(getClass().getSimpleName() + " requires " + UpstartExtension.class.getName())),
            initialTime,
            timezone,
            immediateExecutor,
            interceptedSchedules
    );
  }

  private static boolean hasInitialTime(FakeTimeTest annotation) {
    return !annotation.initialTime().isEmpty() || annotation.value() != FakeTimeTest.UNSPECIFIED_INITIAL_MILLIS;
  }

  private static Instant computeInitialTime(FakeTimeTest annotation) {
    return annotation.initialTime().isEmpty()
            ? Instant.ofEpochMilli(annotation.value())
            : Instant.parse(annotation.initialTime());
  }
}
