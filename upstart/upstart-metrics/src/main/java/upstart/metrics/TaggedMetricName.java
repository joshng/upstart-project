package upstart.metrics;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaggedMetricName {
  private static final Pattern TAG_PATTERN = Pattern.compile("([\\w.-]+)\\[([^]]+)]");
  private static final char ENCODED_TAG_PREFIX = '[';
  private static final char ENCODED_TAG_SUFFIX = ']';
  private static final char ENCODED_TAG_DELIMITER = ',';
  private static final char ENCODED_TAG_VALUE_SEPARATOR = ':';
  private static final Joiner.MapJoiner TAG_MAP_JOINER = Joiner.on(ENCODED_TAG_DELIMITER)
          .withKeyValueSeparator(ENCODED_TAG_VALUE_SEPARATOR);
  private static final Splitter.MapSplitter TAG_MAP_SPLITTER = Splitter.on(ENCODED_TAG_DELIMITER)
          .withKeyValueSeparator(ENCODED_TAG_VALUE_SEPARATOR);
  private static final Splitter TAG_NAME_SPLITTER = Splitter.on('.');
  private final String name;
  private final List<String> nameParts;
  private final Map<String, String> tags;

  private TaggedMetricName(String name, ImmutableList<String> nameParts, Map<String, String> tags) {
    this.name = name;
    this.nameParts = nameParts;
    this.tags = tags;
  }

  public static TaggedMetricName parse(String encodedName) {
    Matcher matcher = TAG_PATTERN.matcher(encodedName);
    String untaggedName;
    Map<String, String> tags;
    if (matcher.find() && matcher.groupCount() == 2) {
      untaggedName = matcher.group(1);
      String tagString = matcher.group(2);
      tags = TAG_MAP_SPLITTER.split(tagString);
    } else {
      untaggedName = encodedName;
      tags = ImmutableMap.of();
    }

    ImmutableList<String> nameParts = ImmutableList.copyOf(TAG_NAME_SPLITTER.split(untaggedName));

    return new TaggedMetricName(untaggedName, nameParts, tags);
  }

  public static String encodedName(String name, Map<String, String> tags) {
    if (tags.isEmpty()) return name;

    StringBuilder builder = new StringBuilder(name)
            .append(ENCODED_TAG_PREFIX);
    return TAG_MAP_JOINER
            .appendTo(builder, tags.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).iterator())
            .append(ENCODED_TAG_SUFFIX)
            .toString();
  }

  public String getName() {
    return name;
  }

  public List<String> getNameParts() {
    return nameParts;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TaggedMetricName that = (TaggedMetricName) o;

    if (!name.equals(that.name)) return false;
    return tags.equals(that.tags);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + tags.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "TaggedMetricName{" +
            "name='" + name + '\'' +
            ", tags=" + tags +
            '}';
  }
}
