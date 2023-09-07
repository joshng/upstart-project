package upstart.util.strings;

import com.google.common.base.Functions;
import upstart.util.collect.Pair;

import java.util.Map;
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

  public static Pair<String,String> splitAroundLast(String str, char delimiter) {
    return splitAroundCharAt(str, str.lastIndexOf(delimiter));
  }

  public static Pair<String,String> splitAroundFirst(String str, String delimiter) {
    return splitAroundCharsAt(str, str.indexOf(delimiter), delimiter.length());
  }

  public static Pair<String,String> splitAroundLast(String str, String delimiter) {
    return splitAroundCharsAt(str, str.lastIndexOf(delimiter), delimiter.length());
  }

  public static Pair<String, String> splitAroundCharAt(String str, int idx) {
    return splitAroundCharsAt(str, idx, 1);
  }

  public static Pair<String, String> splitAroundCharsAt(String str, int skipFromIdx, int skipChars) {
    return Pair.of(str.substring(0, skipFromIdx), str.substring(skipFromIdx + skipChars));
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

  public static String interpolateTokens(String template, Pattern tokenPattern, Map<String, String> tokenReplacement) {
    return interpolateTokens(template, tokenPattern, Functions.forMap(tokenReplacement).compose(matcher -> matcher.group(1)));
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
    return snakify(camelCase).toUpperCase();
  }

  public static String toLowerSnakeCase(String camelCase) {
    return snakify(camelCase).toLowerCase();
  }

  private static String snakify(String camelCase) {
    return EMBEDDED_UPPERCASE.matcher(camelCase).replaceAll("_");
  }
}
