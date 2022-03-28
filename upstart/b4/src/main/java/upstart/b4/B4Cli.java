package upstart.b4;

import com.google.common.base.Strings;
import upstart.b4.config.CommandLineParser;
import upstart.b4.config.TargetRegistry;
import upstart.b4.config.TargetConfigurator;
import upstart.config.ConfigMappingException;
import upstart.services.ServiceSupervisor;
import upstart.util.Ambiance;
import upstart.util.BooleanChoice;
import upstart.util.collect.PairStream;
import upstart.util.strings.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static picocli.CommandLine.ArgGroup;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

public class B4Cli {
  public static final String TARGET_DESCRIPTION_INDENTATION = "\n          ";
  private final Logger LOG;
  private final String[] rawArgs;
  private final InvocationOptions options;
  private final CommandLine commandLine;
  private final InvocationMode invocationMode;

  public B4Cli(String... args) {
    rawArgs = args;

    String programName = Ambiance.ambientValue("b4.program-name").orElse("b4");
    LOG = LoggerFactory.getLogger(programName);

    options = new InvocationOptions();
    commandLine = newCommandLine(options, programName);
    try {
      commandLine.parseArgs(args);
    } catch (Exception e) {
      showUsage(System.err);
      throw exitWithException(e);
    }
    if (options.noColor) System.setProperty("picocli.ansi", "false");
//    commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(options.noColor ? CommandLine.Help.Ansi.OFF : CommandLine.Help.Ansi.ON));
    invocationMode = options.invocationMode();
  }

  public static void main(String... args) {
    new B4Cli(args).run();
  }

  public static CommandLine newCommandLine(Object options, String commandName) {
    return new CommandLine(options)
            .setCommandName(commandName)
            .setStopAtPositional(true)
            .setUnmatchedOptionsArePositionalParams(true)
            ;
  }

  public static Config buildAppConfig(String overrideConfig, Path configFile) {
    Config commandLineConfig = ConfigFactory.parseString(
            overrideConfig,
            ConfigParseOptions.defaults().setOriginDescription("command-line")
    );

    // we could use HOCON's "config.file" property for loading target configs, but that might not compose well into other applications
    return commandLineConfig.withFallback(ConfigFactory.parseFile(configFile.toAbsolutePath().toFile()));
  }

  public void run() {
    String configStr = options.globalConfigs.stream()
            .map(CommandLineParser::applyBoolean)
            .collect(Collectors.joining("\n"));

    Config appConfig = buildAppConfig(configStr, options.configFile);

    TargetRegistry targetRegistry = B4.buildTargetRegistry(appConfig);

    if (invocationMode.showUsage()) {
      showUsage(System.out);
    }

    if (invocationMode.listTargets()) {
      listTargets(targetRegistry, Optional.ofNullable(options.listPattern));
    }

    if (invocationMode.parseCommands()) {
      B4Application app = buildApplication(targetRegistry, invocationMode);

      if (invocationMode.showGraph()) {
        showGraph(app);
      }

      if (invocationMode.showConfigs()) {
        println("\nAssociated targets and their default options:\n\n" + app.getActiveCommandHelp()
                .collect(Collectors.joining("\n\n")));
      }

      if (invocationMode.executeTargets()) {
        executeTargets(app);
      } else {
        println("\n(Run without '-h' or '-g' to execute)");
      }
    }
  }

  private void showUsage(PrintStream stream) {
    commandLine.usage(stream);
    stream.println();
  }

  private void executeTargets(B4Application app) {
    ServiceSupervisor supervisor = ServiceSupervisor.forService(app::getApplication)
            .shutdownGracePeriod(Duration.ofSeconds(300))
            .exitOnUncaughtException(true)
            .build();

     app.failureFuture().thenAccept(e -> {
      if (B4CancellationException.isCancellation(e)) {
        B4.WARN_LOG.warn("Aborted");
      } else {
        B4.WARN_LOG.error("ERROR, execution aborted", e);
      }
      println("@|red,bold TERMINATED ABNORMALLY|@\n" + app.renderStatusGraph());
    });

    supervisor.startAndAwaitTermination();

    if (app.failure().isPresent()) {
      System.exit(1);
    } else if (options.effectiveVerbosity().isEnabled(B4Function.Verbosity.Info)) {
      LOG.info("DONE: {}", String.join(" ", rawArgs));
    }
  }

  private void showGraph(B4Application app) {
    println("\nDescribing command `" + String.join(" ", rawArgs) + "`:");
    println(app.describeExecution(Optional.of(B4Cli::highlightSummary)));
  }

  private B4Application buildApplication(TargetRegistry targetRegistry, InvocationMode invocationMode) {
    B4Application app;
    try {
      List<TargetConfigurator> commands = CommandLineParser.parseCommands(options.commands, options);

      B4Application.ExecutionConfig executionConfig = options.buildExecutionConfig();

      app = new B4Application(
              commands,
              targetRegistry,
              executionConfig
      );
    } catch (ConfigMappingException | CommandLine.PicocliException e) { // TODO: are there other exception-types we should tolerate here?
      if (!invocationMode.showUsage()) showUsage(System.err);
      throw exitWithException(e);
    }
    return app;
  }


  private void println(String msg) {
    println(System.out, msg);
  }

  private void println(PrintStream out, String msg) {
    B4Console.println(out, msg, commandLine.getColorScheme().ansi());
  }

  private Error exitWithException(Exception e) {
    println(System.err, "ERROR:\n" + e.getMessage());
    System.exit(1);
    throw new AssertionError(); // not reached
  }

  private void listTargets(TargetRegistry targetRegistry, Optional<Pattern> filter) {
    Map<TargetName, List<TargetSpec>> byNamespace = targetRegistry.allTargets()
            .filter(spec -> filter.map(f -> f.matcher(spec.name().value()).find()).orElse(true))
            .collect(Collectors.groupingBy(spec -> spec.name().parentNamespace().orElse(TargetName.ROOT)));

    println(String.format(
            """
            Supported targets:\s
            %s

            @|red Notes:|@
            • Underlined namespaces are targets that run all immediate children
            • Learn more about specific command(s) by running them with '-h' (help)
            """,
            PairStream.of(byNamespace)
                    .filterValues(targets -> !targets.stream().allMatch(spec -> byNamespace.containsKey(spec.name())))
                    .sorted(Map.Entry.comparingByKey(TargetName.LEXICAL_COMPARATOR))
                    .flatMap((namespace, targets) ->
                            Stream.concat(Stream.of(
                                    "",
                                    namespaceDescription(namespace, targetRegistry),
                                    " " + Strings.repeat("=", namespace.displayName().length())
                                    ), targets.stream()
                                            .filter(spec -> !byNamespace.containsKey(spec.name()))
                                            .map(target -> "  " + shortTargetDescription(target))
                            )
                    ).collect(Collectors.joining("\n"))
    ));
  }

  private String namespaceDescription(TargetName namespace, TargetRegistry targetRegistry) {
    return namespace.equals(TargetName.ROOT)
            ? B4Console.healthyHighlight(namespace.displayName())
            : targetDescription(targetRegistry.spec(namespace), d -> " -- " + d);
  }

  public static String shortTargetDescription(TargetSpec spec) {
    return targetDescription(spec, d -> TARGET_DESCRIPTION_INDENTATION + d.replaceAll("\n", TARGET_DESCRIPTION_INDENTATION)) + "\n";
  }

  private static String targetDescription(TargetSpec spec, Function<String, String> descriptionFormatter) {
    return B4Console.healthyHighlight(spec.name().displayName()) + spec.description().map(descriptionFormatter).orElse("");
  }

  static String highlightSummary(TargetInvocation service) {
    String msg = service.id().displayName();
    return B4Console.healthyHighlight(msg)
            + "\n" + service.effectiveVerbosity() + " " + service.effectivePhases();
  }

  @CommandLine.Command(sortOptions = false)
  static class InvocationOptions extends TargetOptions {
    @Option(names = {"--no-color"}, description = "Disable ansi-color output")
    boolean noColor;

    @Option(names = {"-l", "--list-targets"}, description = "List available targets (matching <regex> if provided)", arity = "0..1", fallbackValue = ".*", paramLabel = "<regex>")
    Pattern listPattern;

    @Option(names = {"-f", "--config-file"}, description = "HOCON config-file", defaultValue = "B4.conf")
    Path configFile;

    @Option(names = {"-g", "--graph"}, description = "Display the DAG of tasks related to the given @|yellow <commands>|@")
    boolean showGraph;

    @Option(names = {"-D", "--define-config"}, description = "Override config-values. May be global values, or apply to specific actions.\n" +
            "For example, to set the maven path for all tasks:\n" +
            "    -Dbuild.maven.mavenExecutable=my/maven/mvn\n" +
            "Or just for one task:\n" +
            "    -Db4.tasks.build/maven<id>.mavenExecutable=my/maven/mvn" )
    List<String> globalConfigs = new ArrayList<>();

    @Option(names = {"-s", "--skip"}, description = "Target-patterns to be skipped (regex); if specified, targets with matching names (and their dependencies!) will not be executed")
    List<String> skippedTargetPatterns = new ArrayList<>();

    @Option(names = {"-e", "--execute"}, description = "Target-patterns to be executed (regex); if specified, only targets with matching names will be executed")
    List<String> includedTargetPatterns = new ArrayList<>();

    @Option(names = {"-F", "--full-graph"}, description = "Retain no-op targets in execution graph (by default, symbolic targets will be pruned from the graph)", defaultValue = "false", negatable = true)
    boolean fullGraph = false;

    @Option(names = {"-n", "--dry-run"}, description = "Dry run: announce steps that would be performed, but do nothing", defaultValue = "false", negatable = true)
    boolean dryRun = false;

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display this message, and describe all tasks and options related to the given @|yellow <commands>|@")
    boolean helpRequested = false;

    // TODO: mechanism to display configuration, then prompt to execute (or full UI based on JLine and/or lanterna?)
//    @Option(names = { "-y", "--confirm"})
//    boolean confirmed;

    InvocationMode invocationMode() {
      return BooleanChoice
              .of(listPattern != null || commands.isEmpty(), InvocationMode.ListTargets)
              .or(helpRequested, InvocationMode.ShowTargetConfigs)
              .or(showGraph, InvocationMode.ShowTargetGraph)
              .otherwise(InvocationMode.ExecuteTargets);
    }

    B4Application.ExecutionConfig buildExecutionConfig() {
      return B4Application.ExecutionConfig.builder()
              .from(this) // apply TargetExecutionConfig values (verbosity, phases)
              .pruneTargetGraph(!fullGraph)
              .targetConfigInclusion(patternPredicate(skippedTargetPatterns, false))
              .targetExecutionInclusion(patternPredicate(includedTargetPatterns, true))
              .dryRun(dryRun)
              .build();
    }
  }

  public static Predicate<TargetInstanceId> patternPredicate(List<String> patterns, boolean includeMatches) {
    if (patterns.isEmpty()) return target -> true;

    Pattern pattern = Patterns.anyMatch(patterns);
    return patternPredicate(pattern, includeMatches);
  }

  public static Predicate<TargetInstanceId> patternPredicate(Pattern pattern, boolean includeMatches) {
    return target -> pattern.matcher(target.displayName()).find() == includeMatches;
  }

  public static class ExecutionOptions {
    public static final ExecutionOptions DEFAULT = new ExecutionOptions();
    @Option(names = {"-d", "--dirty-run"}, order = 0, description = "just run tasks without cleaning (the default)") boolean dirtyRun;
    @Option(names = {"-c", "--clean-run"}, order = 1, description = "clean residual task results before running") boolean cleanRun;
    @Option(names = {"-C", "--clean-only"}, order = 2, description = "just clean; don't run tasks") boolean cleanOnly;

    public Optional<TargetInvocation.Phases> phases() {
      return BooleanChoice
              .of(cleanOnly, TargetInvocation.Phases.CleanOnly)
              .or(cleanRun, TargetInvocation.Phases.CleanRun)
              .or(dirtyRun, TargetInvocation.Phases.DirtyRun)
              .result();
    }
  }

  public static class VerboseOptions {
    public static final VerboseOptions DEFAULT = new VerboseOptions();
    @Option(names = {"-q", "--quiet"}, order = 0, description = "remain silent unless something goes wrong") boolean quiet;
    @Option(names = {"-i", "--info"}, order = 1, description = "log command-strings and high-level progress (default)") boolean info;
    @Option(names = {"-v", "--verbose"}, order = 2, description = "...also log details and command-output as they happen") boolean verbose;
    @Option(names = {"-V", "--debug"}, order = 3, description = "...also log trace-level diagnostics") boolean debug;

    public Optional<B4Function.Verbosity> verbosity() {
      return BooleanChoice
              .of(quiet, B4Function.Verbosity.Quiet)
              .or(verbose, B4Function.Verbosity.Verbose)
              .or(debug, B4Function.Verbosity.Debug)
              .or(info, B4Function.Verbosity.Info)
              .result();
    }
  }

  public static class TargetOptions implements TargetExecutionConfig {
    @ArgGroup(heading = "\nExecution\n", order = 0)
    ExecutionOptions executionOptions = ExecutionOptions.DEFAULT;

    @ArgGroup(heading = "\nVerbosity\n", order = 1)
    VerboseOptions verboseOptions = VerboseOptions.DEFAULT;

    @Parameters(description = "[<target> [--config=value]...]...")
    public List<String> commands = new ArrayList<>();

    @Override
    public Optional<TargetInvocation.Phases> phases() {
      return executionOptions.phases();
    }

    @Override
    public Optional<B4Function.Verbosity> verbosity() {
      return verboseOptions.verbosity();
    }
  }

  enum InvocationMode {
    ListTargets,
    ShowTargetGraph,
    ShowTargetConfigs,
    ExecuteTargets;

    boolean listTargets() {
      return this == ListTargets;
    }

    boolean showGraph() { return this == ShowTargetGraph || this == ShowTargetConfigs; }

    boolean showUsage() { return this == ListTargets; }

    boolean showConfigs() { return this == ShowTargetConfigs; }

    boolean parseCommands() { return this != ListTargets; }

    boolean executeTargets() { return this == ExecuteTargets; }
  }
}
