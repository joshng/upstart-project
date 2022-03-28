package upstart.util.strings;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Patterns {
  public static Pattern anyMatch(Collection<String> patterns) {
    if (patterns.size() == 0) {
      throw new IllegalArgumentException("Must specify at least one pattern");
    }
    return Pattern.compile(
            patterns.stream()
                    .map(p -> "(?:" + p + ")")
                    .collect(Collectors.joining("|"))
    );
  }

  public static Pattern anyOf(Collection<Pattern> patterns) {
    switch (patterns.size()) {
      case 0:
        throw new IllegalArgumentException("Must specify at least one pattern");
      case 1:
        return patterns.iterator().next();
      default:
        return Pattern.compile(
                patterns.stream()
                        .map(p -> "(?:" + p + ")")
                        .collect(Collectors.joining("|"))
        );
    }
  }
}
