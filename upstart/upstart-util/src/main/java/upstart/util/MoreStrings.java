package upstart.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MoreStrings {
  public static final Pattern SPACES = Pattern.compile(" +");
  public static final Pattern WHITESPACE = Pattern.compile("\\p{javaWhitespace}+");

  public static Pair<String,String> splitAroundFirst(String str, char delimiter) {
    return splitAroundCharAt(str, str.indexOf(delimiter));
  }

  public static Pair<String, String> splitAroundCharAt(String str, int idx) {
    return Pair.of(str.substring(0, idx), str.substring(idx + 1));
  }

  public static Stream<String> splitOnSpaces(String str) {
    return SPACES.splitAsStream(str);
  }

  public static Stream<String> splitOnWhitespace(String str) {
    return WHITESPACE.splitAsStream(str);
  }

  public static String padRight(String str, int requiredWidth, char padChar) {
    if (str.length() >= requiredWidth) return str;
    StringBuilder builder = new StringBuilder(requiredWidth).append(str);
    for (int i = str.length(); i < requiredWidth; i++) {
      builder.append(padChar);
    }
    return builder.toString();
  }

  public static String truncateWithEllipsis(String str, int maxLen) {
    return truncate(str, maxLen, "...");
  }

  public static String truncate(String str, int maxLen, String ellipsis) {
    return str.length() <= maxLen ? str : str.substring(0, maxLen - ellipsis.length()) + ellipsis;
  }

  public static String interpolateTokens(String template, Pattern tokenPattern, Function<Matcher, String> tokenReplacement) {
    return interpolateTokens(template, tokenPattern, new StringBuilder(template.length()), tokenReplacement).toString();
  }

  public static StringBuilder interpolateTokens(String template, Pattern tokenPattern, StringBuilder buffer, Function<Matcher, String> tokenReplacement) {
    Matcher matcher = tokenPattern.matcher(template);
    while (matcher.find()) {
      String replacement = tokenReplacement.apply(matcher);
      matcher.appendReplacement(buffer, replacement);
    }
    matcher.appendTail(buffer);
    return buffer;
  }

  private static final Pattern EMBEDDED_UPPERCASE = Pattern.compile("(?<=[a-z])(?=[A-Z])");
  public static String toUpperSnakeCase(String camelCase) {
    return EMBEDDED_UPPERCASE.matcher(camelCase).replaceAll("_").toUpperCase();
  }
}
