package upstart.util.strings;

/* This file is part of proquint: http://github.com/dsw/proquint .
   See License.txt for copyright and terms of use. */


import java.io.IOException;
import java.io.Reader;

/**
 * Convert between proquint, hex, and decimal strings.
 * Please see the article on proquints: http://arXiv.org/html/0901.4016
 * Daniel S. Wilkerson
 */
public class Proquint {

  /**
   * Map uints to consonants.
   */
  private static final char[] consonants = {
          'b', 'd', 'f', 'g',
          'h', 'j', 'k', 'l',
          'm', 'n', 'p', 'r',
          's', 't', 'v', 'z'
  };

  /**
   * Map uints to vowels.
   */
  private static final char[] vowels = {
          'a', 'i', 'o', 'u'
  };

  public static String quint(long i) {
    return quint(i, '-');
  }

  public static String quint(long value, char sepChar) {
    StringBuilder buf = new StringBuilder(23);
    StringBuilder hiBits = appendQuint(buf, (int) (value >>> 32), sepChar);
//    return appendQuint(hiBits.append(sepChar), (int) i , sepChar).toString();
    return appendQuint(hiBits.append(sepChar), (int) (value & 0xffffffffL), sepChar).toString();
  }

  public static String quint(int i) {
    return quint(i, '-');
  }

  public static String quint(int i, char sepChar) {
    return appendQuint(new StringBuilder(11), i, sepChar).toString();
  }

  public static StringBuilder appendQuint(StringBuilder quint, int i, char sepChar) {
    // http://docs.oracle.com/javase/tutorial/java/nutsandbolts/opsummary.html
    // ">>>" Unsigned right shift
    long j;

    j = (i >>> 28) & 0XF;
    i <<= 4;
    quint.append(consonants[(int) j]);
    j = (i >>> 30) & 0X3;
    i <<= 2;
    quint.append(vowels[(int) j]);
    j = (i >>> 28) & 0XF;
    i <<= 4;
    quint.append(consonants[(int) j]);
    j = (i >>> 30) & 0X3;
    i <<= 2;
    quint.append(vowels[(int) j]);
    j = (i >>> 28) & 0XF;
    i <<= 4;
    quint.append(consonants[(int) j]);

    quint.append(sepChar);

    j = (i >>> 28) & 0XF;
    i <<= 4;
    quint.append(consonants[(int) j]);
    j = (i >>> 30) & 0X3;
    i <<= 2;
    quint.append(vowels[(int) j]);
    j = (i >>> 28) & 0XF;
    i <<= 4;
    quint.append(consonants[(int) j]);
    j = (i >>> 30) & 0X3;
    i <<= 2;
    quint.append(vowels[(int) j]);
    j = (i >>> 28) & 0XF;
    i <<= 4;
    quint.append(consonants[(int) j]);

    return quint;
  }

  /**
   * Convert a proquint to an unsigned int (actually a long int).
   */
  public static long quint2uint(Reader quint) throws IOException {
    long res = 0;

    while (true) {
      final long c = quint.read();
      if (c == -1) break;

      switch ((int) c) {

        /* consonants */
        case 'b':
          res <<= 4;
          res += 0;
          break;
        case 'd':
          res <<= 4;
          res += 1;
          break;
        case 'f':
          res <<= 4;
          res += 2;
          break;
        case 'g':
          res <<= 4;
          res += 3;
          break;

        case 'h':
          res <<= 4;
          res += 4;
          break;
        case 'j':
          res <<= 4;
          res += 5;
          break;
        case 'k':
          res <<= 4;
          res += 6;
          break;
        case 'l':
          res <<= 4;
          res += 7;
          break;

        case 'm':
          res <<= 4;
          res += 8;
          break;
        case 'n':
          res <<= 4;
          res += 9;
          break;
        case 'p':
          res <<= 4;
          res += 10;
          break;
        case 'r':
          res <<= 4;
          res += 11;
          break;

        case 's':
          res <<= 4;
          res += 12;
          break;
        case 't':
          res <<= 4;
          res += 13;
          break;
        case 'v':
          res <<= 4;
          res += 14;
          break;
        case 'z':
          res <<= 4;
          res += 15;
          break;

        /* vowels */
        case 'a':
          res <<= 2;
          res += 0;
          break;
        case 'i':
          res <<= 2;
          res += 1;
          break;
        case 'o':
          res <<= 2;
          res += 2;
          break;
        case 'u':
          res <<= 2;
          res += 3;
          break;

        /* separators */
        default:
          break;
      }
    }

    return res;
  }

}