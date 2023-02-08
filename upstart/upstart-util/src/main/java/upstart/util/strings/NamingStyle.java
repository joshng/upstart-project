package upstart.util.strings;

import com.google.common.collect.Streams;
import upstart.util.collect.MoreStreams;

import java.util.Iterator;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// TODO: these would probably benefit from a streaming toLowerCase/toUpperCase implementation
public enum NamingStyle {
  UpperCamelCase {
    @Override
    public Stream<String> extractWords(String str) {
      return CAMEL.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return words.reduce(sb,
                          (b, w) -> b.append(w.substring(0, 1).toUpperCase()).append(w.substring(1).toLowerCase()),
                          StringBuilder::append
      );
    }
  },
  LowerCamelCase {
    @Override
    public Stream<String> extractWords(String str) {
      return UpperCamelCase.extractWords(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      Iterator<String> iterator = words.iterator();
      if (iterator.hasNext()) {
        sb.append(iterator.next().toLowerCase());
      } else {
        return sb;
      }

      return UpperCamelCase.combineWordsTo(sb, Streams.stream(iterator));
    }
  },
  LowerSnakeCase {
    @Override
    public Stream<String> extractWords(String str) {
      return SNAKE.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return join(sb, words, CharacterCase.LOWER, '_');
    }
  },
  UpperSnakeCase {
    @Override
    public Stream<String> extractWords(String str) {
      return SNAKE.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return join(sb, words, CharacterCase.UPPER, '_');
    }
  },
  LowerKebabCase {
    @Override
    public Stream<String> extractWords(String str) {
      return HYPHEN.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return join(sb, words, CharacterCase.LOWER, '-');
    }
  },
  UpperKebabCase { // would anyone actually use this? <shrug>
    @Override
    public Stream<String> extractWords(String str) {
      return HYPHEN.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return join(sb, words, CharacterCase.UPPER, '-');
    }
  }
  ;

//  public static final Pattern CAMEL = Pattern.compile("(?<!^)(?=[A-Z])");
  public static final Pattern CAMEL = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
  public static final Pattern SNAKE = Pattern.compile("_+");
  public static final Pattern HYPHEN = Pattern.compile("-+");

  StringBuilder join(StringBuilder sb, Stream<String> words, CharacterCase characterCase, char delimiter) {
    int first = sb.length();
    return MoreStreams.foldLeft(sb, words, (b, s) -> {
      if (b.length() != first) b.append(delimiter);
      return b.append(characterCase.apply(s));
    });
  }

  public abstract Stream<String> extractWords(String str);
  public abstract StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words);

  public String convertTo(NamingStyle newStyle, String str) {
    return newStyle.combineWordsTo(new StringBuilder(), extractWords(str)).toString();
  }

  public UnaryOperator<String> converterTo(NamingStyle newStyle) {
    return str -> convertTo(newStyle, str);
  }
}
