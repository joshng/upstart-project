package upstart.b4.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Streams;
import com.google.common.reflect.TypeToken;
import upstart.b4.B4Application;
import upstart.b4.B4TargetGenerator;
import upstart.b4.InvalidB4TargetException;
import upstart.b4.B4Cli;
import upstart.b4.TargetInstanceId;
import upstart.b4.TargetName;
import upstart.b4.TargetSpec;
import upstart.config.ProxyConfigMapper;
import upstart.util.collect.MoreStreams;
import upstart.util.strings.MoreStrings;
import upstart.util.Nothing;
import upstart.util.collect.PairStream;
import upstart.util.reflect.Reflect;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds all defined {@link TargetSpec}s from HOCON configuration, and then hosts them for lookup
 * by {@link TargetName} via {@link #spec}/{@link #targetSpec}
 */
public class TargetRegistry {
  private static final Type GENERATOR_CONFIG_TYPE = findConfigType();
  private static final Predicate<String> NON_TARGET_FIELD = field -> !TargetName.hasRootPrefix(field);
  private static final Predicate<String> NON_VARIANT_FIELD = field -> !TargetName.isVariantName(field);
  private final Map<TargetName, TargetSpec> allTargets;
  private final ProxyConfigMapper configMapper;
  private final Config config;
  private final B4Config b4Config;

  public TargetRegistry(Config bootstrapConfig, ObjectMapper objectMapper) {
    configMapper = new ProxyConfigMapper(objectMapper);
    config = bootstrapConfig.resolve();
    b4Config = configMapper.map(config.getConfig("b4"), B4Config.class);
    allTargets = buildTargets();
  }

  public B4Application buildApplication(String spaceSeparatedCommands, B4Application.ExecutionConfig executionConfig) {
    return buildApplication(MoreStrings.splitOnWhitespace(spaceSeparatedCommands).collect(Collectors.toList()), executionConfig);
  }

  public B4Application buildApplication(List<String> commands, B4Application.ExecutionConfig executionConfig) {
    return buildApplication(CommandLineParser.parseCommands(commands, executionConfig), executionConfig);
  }

  public B4Application buildApplication(Collection<TargetConfigurator> commands, B4Application.ExecutionConfig executionConfig) {
    return new B4Application(commands, this, executionConfig);
  }

  public Config config() {
    return config;
  }

  public Stream<TargetSpec> allTargets() {
    return allTargets.values().stream()
            .filter(spec -> spec.configType().isEmpty())
            .sorted(Comparator.comparing(TargetSpec::name, Comparator.comparing(TargetName::value)));
  }

  public TargetSpec targetSpec(TargetInstanceId targetInstance) {
    return spec(targetInstance.targetName());
  }

  public TargetSpec spec(TargetName name) {
    TargetSpec descriptor = allTargets.get(name);
    if (descriptor == null) {
      throw new InvalidB4TargetException(name);
    }
    return descriptor;
  }

  private Map<TargetName, TargetSpec> buildTargets() {

    Map<TargetName, TargetSpec> explicitTargets = PairStream.withMappedKeys(
            Streams.concat(
                    PairStream.of(b4Config.generators()).flatMap(this::invokeGenerator),
                    PairStream.of(b4Config.functions()).flatMap(this::parseFunctions),
                    expandTemplates().flatMap(this::parseTargets),
                    PairStream.of(b4Config.targets()).flatMap(this::parseTargets)
            ),
            TargetSpec::name
    ).toImmutableMap(TargetSpec::merge);

    Multimap<TargetName, TargetSpec> targetsByNamespace = PairStream.of(explicitTargets)
            .filterValues(spec -> spec.configType().isEmpty() && !spec.name().isVariant())
            .mapKeys(TargetName::parentNamespace)
            .flatMapKeys(Optional::stream)
            .toMultimap(MultimapBuilder.hashKeys().arrayListValues()::build);

    return PairStream.of(targetsByNamespace.asMap())
            .mapValues(TargetRegistry::buildNamespaceTarget)
            .concat(explicitTargets)
            .toImmutableMap(TargetSpec::merge);
  }

  private PairStream<TargetName, ConfigValue> expandTemplates() {
    Map<TemplateId, TemplateConfig> templates = b4Config.targetTemplates();
    return PairStream.of(templates)
            .flatMapPairs((id, template) -> template.templatizeTargets(id, templates));
  }


  private static TargetSpec buildNamespaceTarget(TargetName namespace, Collection<TargetSpec> specs) {
    return TargetSpec.builder(namespace)
            .tasks(
                    specs.stream()
                            .map(TargetSpec::name)
                            .map(TargetName::emptyConfigurator)
                            .collect(Collectors.toList())
            ).build();
  }

  private Stream<TargetSpec> invokeGenerator(String name, B4Config.GeneratorConfig generatorNode) {
    Class<? extends B4TargetGenerator<?>> implClass = generatorNode.impl();
    Object loadedConfig = generatorConfigType(implClass)
            .<Object>map(configType -> configMapper.map(generatorNode.config(), configType))
            .orElse(Nothing.NOTHING);

    B4TargetGenerator<Object> generator = Reflect.blindCast(Reflect.newInstance(implClass));
    return generator.generateTargets(loadedConfig);
  }

  private Stream<TargetSpec> parseFunctions(TargetName prefix, ConfigValue node) {
    return switch (node.valueType()) {
      case STRING -> function(prefix, node.atKey(FunctionConfig.IMPLEMENTATION_FIELD).root());
      case OBJECT -> function(prefix, ((ConfigObject) node));
      case NULL -> Stream.empty();
      default -> throw new IllegalArgumentException("Bad stuff: " + node);
    };
  }

  private Stream<TargetSpec> parseTargets(TargetName prefix, ConfigValue node) {
    return switch (node.valueType()) {
      case LIST -> normalTarget(prefix, node.atKey(TargetConfig.TASKS_FIELD).root());
      case OBJECT -> normalTarget(prefix, (ConfigObject) node);
      case NULL -> Stream.empty();
      default -> throw new IllegalArgumentException("Invalid target-config at '" + prefix + "': " + node);
    };
  }

  private Stream<TargetSpec> function(TargetName name, ConfigObject node) {
    try {
      return Stream.concat(
              Stream.of(node)
                      .filter(obj -> obj.containsKey(FunctionConfig.IMPLEMENTATION_FIELD))
                      .map(obj -> configMapper.map(obj.toConfig(), FunctionConfig.class, NON_TARGET_FIELD).buildSpec(name)),
              parseSubtargets(name, node, this::parseFunctions)
      );
    } catch (Exception e) {
      throw InvalidB4TargetException.wrap(name, e);
    }
  }

  private Stream<TargetSpec> normalTarget(TargetName name, ConfigObject node) {
    return target(name, node, NON_TARGET_FIELD.and(NON_VARIANT_FIELD));
  }

  private Stream<TargetSpec> target(TargetName name, ConfigObject node, Predicate<String> mappedFieldSelector) {
    try {
      Stream<TargetSpec> subtargets = parseSubtargets(name, node, this::parseTargets);

      TargetConfig targetConfig = configMapper.map(node.toConfig(), TargetConfig.class, mappedFieldSelector);
      TargetSpec mainSpec = targetConfig.buildSpec(name);
      Stream<TargetSpec> variants = PairStream.of(node)
              .filterKeys(TargetName::isVariantName)
              .flatMap((variantName, variantNode) ->
                      target(
                              name.variant(variantName),
                              (ConfigObject) variantNode,
                              Predicates.alwaysTrue()
                      ).map(mainSpec::merge)
              );
      // TODO: prevent variants from having subtargets (or fix the `merge` step above to only apply to direct variants)
      return Stream.concat(MoreStreams.prepend(mainSpec, variants), subtargets);
    } catch (Exception e) {
      throw InvalidB4TargetException.wrap(name, e);
    }
  }

  private <T> Stream<T> parseSubtargets(TargetName prefix, ConfigObject object, BiFunction<TargetName, ConfigValue, Stream<T>> entryParser) {
    return PairStream.of(object)
            .filterKeys(TargetName::hasRootPrefix)
            .mapKeys(prefix::append)
            .flatMap(entryParser);
  }

  private Optional<Class<?>> generatorConfigType(Class<? extends B4TargetGenerator<?>> impl) {
    // TODO: cache these for reused task-types?
    return Optional.of(TypeToken.of(impl).resolveType(GENERATOR_CONFIG_TYPE).getType())
            .<Class<?>>map(Class.class::cast)
            .filter(FunctionConfig.CONFIGURED_TYPE);
  }

  private static Type findConfigType() {
    return B4TargetGenerator.class.getTypeParameters()[0];
  }

  public String getTargetHelp(TargetName target) {
    return B4Cli.shortTargetDescription(spec(target)) + "\n" + target.referenceHelp();
  }

}
