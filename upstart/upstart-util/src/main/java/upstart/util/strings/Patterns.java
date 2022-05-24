package upstart.util.strings;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Patterns {
  public static Pattern anyMatch(Collection<String> patterns) {
    return switch(patterns.size()) {
      case 0 -> throw new IllegalArgumentException("Must specify at least one pattern");
      case 1 -> Pattern.compile(patterns.iterator().next());
      default -> Pattern.compile(
              patterns.stream()
                      .map(p -> "(?:" + p + ")")
                      .collect(Collectors.joining("|"))
      );
    };
  }

  public static Pattern anyOf(Collection<Pattern> patterns) {
    return switch (patterns.size()) {
      case 0 -> throw new IllegalArgumentException("Must specify at least one pattern");
      case 1 -> patterns.iterator().next();
      default -> Pattern.compile(
              patterns.stream()
                      .map(p -> "(?:" + p + ")")
                      .collect(Collectors.joining("|"))
      );
    };
  }
}
