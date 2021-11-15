package upstart.b4;

import com.google.common.truth.Truth8;
import upstart.services.UpstartService;
import upstart.commandExecutor.CommandResult;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class B4ApplicationTest {
  private static final Config TEST_CONFIG = ConfigFactory.parseResources("test-targets.conf");
  private static final String DEPENDENT_TARGET = "copy-file-again";
  private static final String TASK_1 = "file/copy:hosts";
  private static final String TASK_2 = "file/copy:again";
  private static final String OVERRIDE_CONFIG = "override-config";
  private static final B4Application.ExecutionConfig EXECUTION_CONFIG = B4Application.defaultExecutionConfig()
          .withVerbosity(B4Function.Verbosity.Quiet);

  @Captor ArgumentCaptor<FakeFunction.FakeConfig> configCaptor;

  @Test
  void testCommandLineConfigs() {
    B4Application app = buildApplication(DEPENDENT_TARGET, TASK_1, "--from=overridden-value");

    TargetInvocation invocation = getInvocation(TASK_1, app);
    assertThat(invocation.isolatedConfig().getString("from")).isEqualTo("overridden-value");
  }

  @Test
  void testTargetConfigOverrides() {
    B4Application app = buildApplication(DEPENDENT_TARGET, OVERRIDE_CONFIG);

    assertThat(getInvocation(TASK_1, app).isolatedConfig().getString("from")).isEqualTo("somewhere-else");
    assertThat(getInvocation(TASK_2, app).isolatedConfig().getString("from")).isEqualTo("/tmp/hosts");
  }


  @Test
  void testComplexOverrides() throws Exception {
    B4Application app = buildApplication(OVERRIDE_CONFIG);

    UpstartService application = app.getApplication();
    FakeFunction mock = application.getInstance(FakeFunction.class);

    application.startAsync().awaitTerminated();

    verify(mock).run(configCaptor.capture(), Mockito.any(B4TargetContext.class));

    FakeFunction.FakeConfig config = configCaptor.getValue();

    assertThat(config.string()).isEqualTo("goodbye");
    assertThat(config.number()).isEqualTo(99);
  }

  @Test
  void directTaskInvocation() throws Exception {
    B4Application app = buildApplication("fake/function:hello", "--number=999");

    UpstartService application = app.getApplication();
    FakeFunction mock = application.getInstance(FakeFunction.class);

    application.startAsync().awaitTerminated();

    verify(mock).run(configCaptor.capture(), Mockito.any(B4TargetContext.class));

    FakeFunction.FakeConfig config = configCaptor.getValue();

    assertThat(config.string()).isEqualTo("hello");
    assertThat(config.number()).isEqualTo(999);
  }

  @Test void dependenciesAreConstructed() {
    B4Application app = buildApplication(DEPENDENT_TARGET);
    TargetInvocation dependentTaskInvocation = app.getInvocation(TargetInstanceId.of(TASK_2)).get();
    Stream<TargetInvocation> dependencies = app.getInvocationGraph().predecessors(dependentTaskInvocation);
    Truth8.assertThat(
            dependencies
                    .map(TargetInvocation::id)
                    .map(TargetInstanceId::displayName)
    ).contains(TASK_1);
  }

  @Test void processExecFunctionTimesOut() throws InterruptedException {
    B4Application app = buildApplication("process/exec:sleep-late");
    B4TaskFailedException e = assertThrows(B4TaskFailedException.class, app::runOrThrow);
    assertThat(e).hasCauseThat().isInstanceOf(CommandResult.CommandTimeoutException.class);
  }

  private static TargetInvocation getInvocation(String id, B4Application app) {
    return app.getInvocation(TargetInstanceId.of(id)).get();
  }

  private static B4Application buildApplication(String... commands) {
    return B4.buildTargetRegistry(TEST_CONFIG)
            .buildApplication(Arrays.asList(commands), EXECUTION_CONFIG);
  }
}