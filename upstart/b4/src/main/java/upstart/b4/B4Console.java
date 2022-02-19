package upstart.b4;

import picocli.CommandLine;
import upstart.util.MoreStrings;

import java.io.PrintStream;
import java.util.regex.Pattern;

public class B4Console {
  static final char HIGHLIGHT_MARKER = '\u2007';
  static final char UNHEALTHY_HIGHLIGHT_MARKER = '\u00A0';
  static final char NOTICE_HIGHLIGHT_MARKER = '\u2008';
  static final char NOTICE_LOWLIGHT_MARKER = '\u2009';
  static final Pattern HIGHLIGHT_PLACEHOLDER_PATTERN = Pattern.compile(String.format("%1$s.*?%1$s|%2$s.*?%2$s|%3$s.*?%3$s|%4$s.*?%4$s", HIGHLIGHT_MARKER, UNHEALTHY_HIGHLIGHT_MARKER, NOTICE_HIGHLIGHT_MARKER, NOTICE_LOWLIGHT_MARKER));
  static final String HIGHLIGHT_FORMAT = "green";
  static final String UNHEALTHY_HIGHLIGHT_FORMAT = "red";
  static final String NOTICE_HIGHLIGHT_FORMAT = "yellow";
  static final String NOTICE_LOWLIGHT_FORMAT = "faint";

  private final CommandLine.Help.Ansi ansi;

  public B4Console() {
    this(CommandLine.Help.Ansi.AUTO);
  }

  public B4Console(CommandLine.Help.Ansi ansi) {
    this.ansi = ansi;
  }

  public String renderHighlightPlaceholders(String msg) {
    return renderHighlightPlaceholders(msg, ansi);
  }

  public static String healthyHighlight(String msg) {
    return HIGHLIGHT_MARKER + msg + HIGHLIGHT_MARKER;
  }

  public static String noticeHighlight(String msg) {
    return NOTICE_HIGHLIGHT_MARKER + msg + NOTICE_HIGHLIGHT_MARKER;
  }

  public static String noticeLowlight(String msg) {
    return NOTICE_LOWLIGHT_MARKER + msg + NOTICE_LOWLIGHT_MARKER;
  }

  public static String unhealthyHighlight(String msg) {
    return UNHEALTHY_HIGHLIGHT_MARKER + msg + UNHEALTHY_HIGHLIGHT_MARKER;
  }

  public static void println(PrintStream out, String msg, CommandLine.Help.Ansi ansi) {
    out.println(renderHighlightPlaceholders(msg, ansi));
  }

  public static String renderHighlightPlaceholders(String msg, CommandLine.Help.Ansi ansi) {
    return ansi.string(MoreStrings.interpolateTokens(msg, HIGHLIGHT_PLACEHOLDER_PATTERN, matcher -> {
      String match = matcher.group();
      String format = switch (match.charAt(0)) {
        case HIGHLIGHT_MARKER -> HIGHLIGHT_FORMAT;
        case UNHEALTHY_HIGHLIGHT_MARKER -> UNHEALTHY_HIGHLIGHT_FORMAT;
        case NOTICE_HIGHLIGHT_MARKER -> NOTICE_HIGHLIGHT_FORMAT;
        case NOTICE_LOWLIGHT_MARKER -> NOTICE_LOWLIGHT_FORMAT;
        default -> throw new IllegalStateException("Unexpected format-match: " + match.charAt(0));
      };
      return " @|" + format + " " + match.substring(1, match.length() - 1) + "|@ ";
    }));
  }
}
