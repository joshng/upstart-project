package upstart.commandExecutor;

import upstart.test.BaseSingletonParameterResolver;
import upstart.test.UpstartExtension;
import upstart.test.UpstartTest;
import upstart.test.UpstartTestBuilder;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A junit-jupiter extension which mocks the upstart {@link CommandExecutor} to allow
 * the results of external process invocations to be stubbed.
 *
 * Example:
 * <pre>{@code
 * @UpstartTest
 * @ExtendWith(MockCommandExtension.class)
 * class MyTestClass extends UpstartModule {
 *
 *   @Inject MyCommandComponent component; // a class that uses a CommandExecutor
 *
 *   @Override
 *   protected void configure() {
 *     install(new MyModule());
 *   }
 *
 *   @Test
 *   void testCommand(CommandFixture commandFixture) {
 *     CommandSpec expectedSpec = CommandSpec.of(Duration.ofSeconds(1), "ls", "-a");
 *
 *     commandFixture.mockCommand(
 *             expectedSpec,
 *             CommandResult.Completed.builder()
 *                     .command(expectedSpec)
 *                     .exitCode(0)
 *                     .outputString(".\n..\n.git\npom.xml\nsrc\ntarget")
 *                     .errorString("")
 *                     .build()
 *             ));
 *
 *     component.runCommands();
 *
 *     commandFixture.assertCommand(expectedSpec);
 *   }
 * }
 * }</pre>
 *
 * @see CommandFixture
 */
public class MockCommandExtension extends BaseSingletonParameterResolver<CommandFixture> implements BeforeEachCallback {

  public MockCommandExtension() {
    super(CommandFixture.class);
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    CommandFixture fixture = getOrCreateContext(context);
    UpstartTestBuilder testBuilder = UpstartExtension.getOptionalTestBuilder(context)
            .orElseThrow(() -> new IllegalStateException(getClass().getSimpleName() + " must be applied with "
                    + UpstartTest.class.getSimpleName())
            );
    installMockExecutor(fixture, testBuilder);
  }

  public static CommandFixture installMockExecutor(UpstartTestBuilder testBuilder) {
    CommandFixture fixture = new CommandFixture();
    installMockExecutor(fixture, testBuilder);
    return fixture;
  }

  private static void installMockExecutor(CommandFixture fixture, UpstartTestBuilder testBuilder) {
    testBuilder
            .overrideBindings(binder -> {
              binder.bind(CommandExecutorSPI.class).toInstance(fixture.mock);
              binder.bind(CommandFixture.class).toInstance(fixture);
            });
  }

  @Override
  public CommandFixture createContext(ExtensionContext extensionContext) {
    return new CommandFixture();
  }
}
