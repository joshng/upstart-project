package upstart.b4.config;

import upstart.b4.B4Cli;
import upstart.b4.TargetExecutionConfig;
import upstart.b4.TargetInstanceId;
import upstart.config.ConfigMappingException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class CommandLineParser {
  private final List<String> command;
  private final Map<Integer, List<String>> argLists;
  private TargetExecutionConfig executionConfig;

  public static List<TargetConfigurator> parseCommands(List<String> command, TargetExecutionConfig config) {
    return parseCommands(command, config, "command-line");
  }

  public static List<TargetConfigurator> parseCommands(List<String> command, TargetExecutionConfig config, String argSource) {
    return new CommandLineParser(command, config).buildTargetConfigurators(argSource);
  }

  private CommandLineParser(List<String> command, TargetExecutionConfig executionConfig) {
    this.command = command;
    argLists = command.stream()
            .collect(Collectors.groupingBy(new Function<String, Integer>() {
              int currentTargetIdx = 0;

              @Override
              public Integer apply(String arg) {
                if (!FlagPrefix.IS_FLAG_ARG.test(arg)) currentTargetIdx++;
                return currentTargetIdx;
              }
            }));
    this.executionConfig = executionConfig;
  }

  public List<TargetConfigurator> buildTargetConfigurators(String argSource) {
    List<String> unexpectedPrefix = argLists.get(0);
    checkArgument(unexpectedPrefix == null, "Config-flags cannot precede targets (%s: '%s'): %s", argSource, unexpectedPrefix, command);
    return argLists.values().stream()
              .map(tokens -> parseTarget(tokens, argSource))
              .collect(Collectors.toList());
  }

  private TargetConfigurator parseTarget(List<String> args, String argSource) {
    String name = args.remove(0);
    TargetInstanceId id = TargetInstanceId.of(name);

    B4Cli.TargetOptions options = new B4Cli.TargetOptions();
    if (!args.isEmpty()) {
      CommandLine commandLine = B4Cli.newCommandLine(options, name);
      commandLine.parseArgs(args.toArray(new String[0]));
    }
    Config config = toConfig(Optional.of(id), argSource, options.commands.stream());
    return TargetConfigurator.builder()
            .from(executionConfig)
            .from(options)
            .target(id)
            .config(config)
            .build();
  }

  private static Config toConfig(Optional<TargetInstanceId> task, String argSource, Stream<String> args) {
    String configStr = args
            .map(arg -> parseConfigFlag(task, arg))
            .collect(Collectors.joining("\n"));

    try {
      return ConfigFactory.parseString(configStr, ConfigParseOptions.defaults().setOriginDescription(argSource));
    } catch (Exception e) {
      throw new ConfigMappingException("Unable to parse config: '" + configStr + "':\n  " + e.getMessage(), e);
    }
  }

  private static String parseConfigFlag(Optional<TargetInstanceId> target, String arg) {
    String argContent = FlagPrefix.Local.strip(arg);
    String adjusted = applyBoolean(argContent);
    return target.map(t -> t.instanceConfigPath(adjusted)).orElse(adjusted);
  }

  /**
   *  Extract the initial key portion of an arbitrary config-string (delimited by ':' or '='),
   *  with correct handling of quoted strings (`"config:setting"=3`) and escaped characters (`config\:setting=3`).
   *  <p/>
   *  Then apply boolean settings: if no assigment is specified (`--config.flag`), assign true (`config.flag=true`),
   *  or false if the name starts with "no-" (`--no-config.flag` --> `config.flag=false`)
   */
  public static String applyBoolean(String arg) {
    KeyParseState state = KeyParseState.Searching;
    int len = arg.length();
    for (int i = 0; i < len; i++) {
      state = state.accept(arg.charAt(i));
      if (state == KeyParseState.Done) return arg;
    }

    if (arg.startsWith("no-")) {
      return arg.substring(3) + "=false";
    } else {
      return arg + "=true";
    }
  }

  private enum KeyParseState {
    Searching {
      @Override
      KeyParseState accept(char c) {
        switch (c) {
          case '+':
          case '=':
          case ':':
            return Done;
          case '"':
            return Quoted;
          case '\\':
            return Escaped;
          default:
            return Searching;
        }
      }
    },
    Quoted {
      @Override
      KeyParseState accept(char c) {
        switch (c) {
          case '"':
            return Searching;
          case '\\':
            return Escaped;
          default:
            return Quoted;
        }
      }
    },
    Escaped {
      @Override
      KeyParseState accept(char c) {
        return Quoted;
      }
    },
    Done {
      @Override
      KeyParseState accept(char c) {
        throw new AssertionError();
      }
    };

    abstract KeyParseState accept(char c);
  }

  /**
   * Originally intended to support different types of command-line flags (but not currently used that way)
   */
  public enum FlagPrefix implements com.google.common.base.Predicate<String> {
    Local("--");

//    public static final Predicate<String> IS_FLAG_ARG = Predicates.or(values());
    public static final Predicate<String> IS_FLAG_ARG = Local.or(s -> s.startsWith("-"));
    final String prefix;

    FlagPrefix(String prefix) {
      this.prefix = prefix;
    }


    public String strip(String arg) {
      checkArgument(test(arg), "Invalid argument (must start with '%s'): '%s'", prefix, arg);
      return arg.substring(prefix.length());
    }

    @Override
    public boolean apply(String s) {
      return s.startsWith(prefix);
    }
  }

}
