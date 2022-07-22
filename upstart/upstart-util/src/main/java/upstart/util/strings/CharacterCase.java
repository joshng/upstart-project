package upstart.util.strings;

import java.util.Locale;
import java.util.function.UnaryOperator;

public enum CharacterCase implements UnaryOperator<String> {
  UPPER() {
    public String apply(Locale locale, String s) {
      return s.toUpperCase(locale);
    }

    @Override
    public String apply(String s) {
      return s.toUpperCase();
    }
  },
  LOWER() {
    @Override
    public String apply(Locale locale, String s) {
      return s.toLowerCase(locale);
    }

    @Override
    public String apply(String s) {
      return s.toLowerCase();
    }
  };

  public abstract String apply(Locale locale, String s);
}
