package upstart.config;

import com.google.common.base.Joiner;
import upstart.util.strings.MoreStrings;
import upstart.util.collect.PairStream;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigDump {
  // TODO: configurable suppression patterns, redaction rules, formatting, etc?
  private static final String DUMP_LINE_PREFIX = "  ";

  public static String describe(Config config) {
    List<ValueDump> values = describeValues(config, 128).toList();

    int longestKey = 0;
    int longestValue = 0;

    for (ValueDump dump : values) {
      if (longestKey < dump.key().length()) longestKey = dump.key().length();
      if (longestValue < dump.value().length()) longestValue = dump.value().length();
    }

    StringBuilder dumpBuilder = new StringBuilder(DUMP_LINE_PREFIX);
    String format = "%-" + longestKey + "s = %-" + longestValue + "s  # %s";
    Joiner.on("\n" + DUMP_LINE_PREFIX).appendTo(dumpBuilder,
            values.stream().sorted().map(
                    dump -> String.format(
                            format,
                            dump.key(),
                            dump.value(),
                            dump.origin()))
                    .iterator());
    return dumpBuilder.toString();
  }

  public static Stream<ValueDump> describeValues(Config config, int maxValueLength) {
    return PairStream.of(config.entrySet())
              .map((k, v) -> new ValueDump(k, v, maxValueLength))
              .sorted();
  }

  public static class ValueDump implements Comparable<ValueDump> {
    private final String key;
    private final String value;
    private final String origin;

    ValueDump(String key, ConfigValue configValue, int maxValueLength) {
      this.key = key;
      String string;
      // config may contain sensitive values that are probably best kept out of logs (eg, API keys,
      // fake user-credentials in development mode, and anything else that might evade best-practices); try to
      // redact them here
      if ((configValue.valueType() == ConfigValueType.STRING && looksSensitive(key))
              || looksSensitive(string = String.valueOf(configValue.unwrapped()))) {
        value = "<..redacted..>";
      } else {
        value = MoreStrings.truncateWithEllipsis(string, maxValueLength);
      }

      origin = configValue.origin().description();
    }

    private static boolean looksSensitive(String value) {
      String lowercase = value.toLowerCase();
      return lowercase.contains("password") || lowercase.contains("secret");
    }

    @Override
    public int compareTo(ValueDump o) {
      return key.compareTo(o.key);
    }

    public String key() {
      return key;
    }

    public String value() {
      return value;
    }

    public String origin() {
      return origin;
    }
  }
}

