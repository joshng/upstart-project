package com.google.common.io;


import upstart.util.strings.CharacterCase;

import java.util.regex.Pattern;

public class CrockfordBase32Encoding extends BaseEncoding.StandardBaseEncoding {
  private static final BaseEncoding UPPERCASE = new CrockfordBase32Encoding(CharacterCase.UPPER);
  private static final BaseEncoding LOWERCASE = new CrockfordBase32Encoding(CharacterCase.LOWER);
  public static final String UPPERCASE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  public static final String LOWERCASE_ALPHABET = UPPERCASE_ALPHABET.toLowerCase();

  private static final Pattern U_PATTERN = Pattern.compile("[Uu]");
  private final String letter_v;
  private final CharacterCase characterCase;

  public static BaseEncoding lowerCaseInstance() {
    return LOWERCASE;
  }

  public static BaseEncoding upperCaseInstance() {
    return UPPERCASE;
  }

  public static BaseEncoding instance(CharacterCase characterCase) {
    return switch (characterCase) {
      case UPPER -> UPPERCASE;
      case LOWER -> LOWERCASE;
    };
  }

  private CrockfordBase32Encoding(CharacterCase characterCase) {
    super("CrockfordBase32", characterCase.apply(UPPERCASE_ALPHABET), null);
    letter_v = characterCase.apply("v");
    this.characterCase = characterCase;
  }

  @Override
  public BaseEncoding upperCase() {
    return UPPERCASE;
  }

  @Override
  public BaseEncoding lowerCase() {
    return LOWERCASE;
  }

  @Override
  int decodeTo(byte[] target, CharSequence chars) throws DecodingException {
    return super.decodeTo(target, characterCase.apply(U_PATTERN.matcher(chars).replaceAll(letter_v)));
  }
}
