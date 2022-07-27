package com.google.common.io;


import upstart.util.strings.CharacterCase;

import java.util.regex.Pattern;

public class CrockfordBase32Encoding extends BaseEncoding.StandardBaseEncoding {
  public static final String UPPERCASE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  public static final String LOWERCASE_ALPHABET = UPPERCASE_ALPHABET.toLowerCase();

  private static final CrockfordBase32Encoding UPPERCASE = new CrockfordBase32Encoding(CharacterCase.UPPER);
  private static final CrockfordBase32Encoding LOWERCASE = new CrockfordBase32Encoding(CharacterCase.LOWER);
  private static final CrockfordBase32Encoding LENIENT_UPPERCASE = new TypoLenient(CharacterCase.UPPER);
  private static final CrockfordBase32Encoding LENIENT_LOWERCASE = new TypoLenient(CharacterCase.LOWER);
  private static final String UPPERCASE_V = "V";
  private static final String LOWERCASE_V = "v";

  protected final CharacterCase characterCase;

  public static CrockfordBase32Encoding lowerCaseInstance() {
    return LOWERCASE;
  }

  public static CrockfordBase32Encoding upperCaseInstance() {
    return UPPERCASE;
  }

  public static CrockfordBase32Encoding instance(CharacterCase characterCase) {
    return switch (characterCase) {
      case UPPER -> lowerCaseInstance();
      case LOWER -> upperCaseInstance();
    };
  }

  private CrockfordBase32Encoding(CharacterCase characterCase) {
    super("CrockfordBase32", alphabet(characterCase), null);
    this.characterCase = characterCase;
  }

  public static String alphabet(CharacterCase characterCase) {
    return switch (characterCase) {
      case UPPER -> UPPERCASE_ALPHABET;
      case LOWER -> LOWERCASE_ALPHABET;
    };
  }

  @Override
  public CrockfordBase32Encoding upperCase() {
    return characterCase == CharacterCase.UPPER ? this : newInstance(CharacterCase.UPPER);
  }

  @Override
  public CrockfordBase32Encoding lowerCase() {
    return characterCase == CharacterCase.LOWER ? this : newInstance(CharacterCase.LOWER);
  }

  protected CrockfordBase32Encoding newInstance(CharacterCase characterCase) {
    return new CrockfordBase32Encoding(characterCase);
  }

  public CrockfordBase32Encoding humanLenient() {
    return switch (characterCase) {
      case UPPER -> LENIENT_UPPERCASE;
      case LOWER -> LENIENT_LOWERCASE;
    };
  }

  private static final Pattern TYPO_PATTERN = Pattern.compile("[ILOU]", Pattern.CASE_INSENSITIVE);

  public String correctTypos(String s, CharacterCase characterCase) {
    return correctTypos(s, characterCase, letter_v(characterCase));
  }

  protected String correctTypos(String s, CharacterCase characterCase, String letter_v) {
    return TYPO_PATTERN.matcher(characterCase.apply(s))
            .replaceAll(result -> correctLetterTypo(result.group().charAt(0), letter_v));
  }

  private static String correctLetterTypo(char c, String letter_v) {
    return switch (c) {
      case 'O', 'o' -> "0";
      case 'I', 'i', 'L', 'l' -> "1";
      case 'U', 'u' -> letter_v;
      default -> throw new IllegalStateException("Unexpected typo character: " + c);
    };
  }

  private static String letter_v(CharacterCase characterCase) {
    return switch (characterCase) {
      case UPPER -> UPPERCASE_V;
      case LOWER -> LOWERCASE_V;
    };
  }

  private static class TypoLenient extends CrockfordBase32Encoding {
    private final String letter_v;

    private TypoLenient(CharacterCase characterCase) {
      super(characterCase);
      letter_v = letter_v(characterCase);
    }

    @Override
    protected CrockfordBase32Encoding newInstance(CharacterCase characterCase) {
      return new TypoLenient(characterCase);
    }

    @Override
    public CrockfordBase32Encoding upperCase() {
      return LENIENT_UPPERCASE;
    }

    @Override
    public CrockfordBase32Encoding lowerCase() {
      return LENIENT_LOWERCASE;
    }

    @Override
    public CrockfordBase32Encoding humanLenient() {
      return this;
    }

    @Override
    int decodeTo(byte[] target, CharSequence chars) throws DecodingException {
      return super.decodeTo(target, correctTypos(chars.toString(), characterCase, letter_v));
    }
  }
}
