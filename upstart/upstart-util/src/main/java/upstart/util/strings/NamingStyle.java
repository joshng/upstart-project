package upstart.util.strings;

import com.google.common.collect.Streams;
import upstart.util.collect.MoreStreams;

import java.util.Iterator;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// TODO: these would probably benefit from a streaming toLowerCase/toUpperCase implementation
public enum NamingStyle {
  UpperCamelCaseSplittingAcronyms {
    @Override
    public Stream<String> extractWords(String str) {
      return CAMEL_SPLITTING_ACRONYMS.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return words.reduce(sb,
                          (b, w) -> b.append(w.substring(0, 1).toUpperCase()).append(w.substring(1).toLowerCase()),
                          StringBuilder::append
      );
    }
  },
  LowerCamelCaseSplittingAcronyms {
    @Override
    public Stream<String> extractWords(String str) {
      return UpperCamelCaseSplittingAcronyms.extractWords(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      Iterator<String> iterator = words.iterator();
      if (iterator.hasNext()) {
        sb.append(iterator.next().toLowerCase());
      } else {
        return sb;
      }

      return UpperCamelCaseSplittingAcronyms.combineWordsTo(sb, Streams.stream(iterator));
    }
  },
  UpperCamelCaseGroupingAcronyms {
    @Override
    public Stream<String> extractWords(String str) {
      return CAMEL_GROUPING_ACRONYMS.splitAsStream(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return words.reduce(sb,
                          (b, w) -> b.append(w.substring(0, 1).toUpperCase()).append(w.substring(1).toLowerCase()),
                          StringBuilder::append
      );
    }
  },
  LowerCamelCaseGroupingAcronyms {
    @Override
    public Stream<String> extractWords(String str) {
      return UpperCamelCaseGroupingAcronyms.extractWords(str);
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      Iterator<String> iterator = words.iterator();
      if (iterator.hasNext()) {
        sb.append(iterator.next().toLowerCase());
      } else {
        return sb;
      }

      return UpperCamelCaseSplittingAcronyms.combineWordsTo(sb, Streams.stream(iterator));
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
  },
  UpperSpaceSeparated {
    @Override
    public Stream<String> extractWords(String str) {
      return Stream.of(str.split("\\s+"));
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return join(sb, words, CharacterCase.UPPER, ' ');
    }
  },
  LowerSpaceSeparated {
    @Override
    public Stream<String> extractWords(String str) {
      return Stream.of(str.split("\\s+"));
    }

    @Override
    public StringBuilder combineWordsTo(StringBuilder sb, Stream<String> words) {
      return join(sb, words, CharacterCase.LOWER, ' ');
    }
  }
  ;

  public static final NamingStyle UpperCamelCase = UpperCamelCaseGroupingAcronyms;
  public static final NamingStyle LowerCamelCase = LowerCamelCaseGroupingAcronyms;

  private static final Pattern CAMEL_SPLITTING_ACRONYMS = Pattern.compile("(?<!^)(?=[A-Z])");
  private static final Pattern CAMEL_GROUPING_ACRONYMS = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
  private static final Pattern SNAKE = Pattern.compile("_+");
  private static final Pattern HYPHEN = Pattern.compile("-+");

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
    return appendConvertedTo(newStyle, str, new StringBuilder()).toString();
  }

  public StringBuilder appendConvertedTo(NamingStyle newStyle, String str, StringBuilder sb) {
    return newStyle != this
            ? newStyle.combineWordsTo(sb, extractWords(str))
            : sb.append(str);
  }

  public UnaryOperator<String> converterTo(NamingStyle newStyle) {
    return str -> convertTo(newStyle, str);
  }
}
