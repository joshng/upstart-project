package upstart.b4;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.io.Resources;
import upstart.b4.config.TargetConfigurator;
import upstart.config.HojackConfigProvider;
import upstart.util.Optionals;
import upstart.util.StringIdentifier;
import upstart.util.Tuple;
import upstart.util.exceptions.UncheckedIO;
import com.typesafe.config.Config;
import org.immutables.value.Value;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable(intern = true)
@Tuple
public abstract class TargetName extends StringIdentifier {
  private static final String ROOT_VALUE = "/";
  public static final TargetName ROOT = TargetName.of(ROOT_VALUE);
  public static final Comparator<TargetName> LEXICAL_COMPARATOR = Comparator.comparing(TargetName::value);

  private static final String NAME_SEGMENT = "[a-zA-Z0-9_][a-zA-Z0-9_-]*?";
  private static final Pattern VALID_NAME = Pattern.compile(String.format("/%s(?:/%s)*(?:~[a-zA-Z0-9]+)+", NAME_SEGMENT, NAME_SEGMENT));
  private static final char NAMESPACE_SEPARATOR = '/';
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile(ROOT_VALUE);
  public static final String VARIANT_SEPARATOR = "%";

  @JsonCreator
  public static TargetName of(String value) {
    return ImmutableTargetName.of(ensureRooted(value));
  }

  private static String ensureRooted(String value) {
    return hasRootPrefix(value) ? value : ROOT_VALUE + value;
  }

  public static boolean hasRootPrefix(String value) {
    return value.startsWith(ROOT_VALUE);
  }

  public TargetInstanceId instanceId(String id) {
    return TargetInstanceId.of(this, id);
  }

  @Value.Lazy
  public TargetInstanceId defaultInstance() {
    return instanceId("");
  }

  public TargetConfigurator emptyConfigurator() {
    return defaultInstance().emptyConfigurator();
  }

  public TargetName append(TargetName subtask) {
    return isRoot() ? subtask : of(value() + subtask.value());
  }

  public TargetName append(String subtask) {
    String adjusted = ensureRooted(subtask);
    return of(isRoot() ? adjusted : value() + adjusted);
  }

  public TargetName variant(String variant) {
    checkArgument(isVariantName(variant), "Must begin with '%s': '%s'", VARIANT_SEPARATOR, variant);
    return of(value() + variant);
  }

  public static boolean isVariantName(String name) {
    return name.startsWith(VARIANT_SEPARATOR);
  }

  public boolean isVariant() {
    return value().indexOf(VARIANT_SEPARATOR) > 0;
  }
//  public boolean isVariantOf(TargetName parent) {
//    return parent.equals(this) || value().startsWith(parent.value()) && VARIANT_PATTERN.matcher(value().substring(parent.value().length())).matches();
//  }

  private boolean isRoot() {
    return value().isEmpty();
  }

  @Value.Derived
  @Value.Auxiliary
  public String baseName() {
    return value().substring(lastSeparatorIdx());
  }

  @Value.Derived
  @Value.Auxiliary
  public int lastSeparatorIdx() {
    return value().lastIndexOf(NAMESPACE_SEPARATOR);
  }

  @Value.Derived
  @Value.Auxiliary
  public Optional<TargetName> parentNamespace() {
    return Optionals.onlyIfFrom(hasNamespace(), () -> of(value().substring(0, lastSeparatorIdx())));
  }

  @Value.Derived
  @Value.Auxiliary
  public String displayName() {
    return value().equals(ROOT_VALUE) ? "[no namespace]" : value().substring(1);
  }

  @Value.Lazy
  public String configPath() {
    return toConfigPath(displayName());
  }

  @Value.Lazy
  public Config referenceConfig() {
    return HojackConfigProvider.getReferenceConfig(configPath());
  }

  @Value.Lazy
  public String referenceHelp() {
    return UncheckedIO.getUnchecked(() -> {
      StringBuilder builder = new StringBuilder();
      Enumeration<URL> urls = ClassLoader.getSystemClassLoader()
              .getResources(HojackConfigProvider.referenceConfigPath(configPath()) + ".conf");
      boolean first = true;
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        if (first) {
          first = false;
        } else {
          builder.append("\n");
        }
        Resources.readLines(url, StandardCharsets.UTF_8).forEach(line -> builder.append("| ").append(line).append("\n"));
      }

      if (builder.length() > 0) {
        return builder.insert(0, "@|underline reference defaults & docs|@\n").toString();
      } else {
        return "";
      }
    });
  }

  public static String toConfigPath(String value) {
    return SEPARATOR_PATTERN.matcher(value).replaceAll(".");
  }

  public boolean hasNamespace() {
    return lastSeparatorIdx() > 0;
  }

  @Override
  protected Pattern validationPattern() {
    return VALID_NAME;
  }

  public boolean isInNamespace(TargetName target) {
    String prefix = value() + NAMESPACE_SEPARATOR;
    return target.value().length() == prefix.length() + target.baseName().length() && target.value().startsWith(prefix);
  }

  @Override
  protected void checkValidValue() {
    if (!value().isEmpty()) super.checkValidValue();
  }

  @Override
  public String toString() {
    return value();
  }
}
