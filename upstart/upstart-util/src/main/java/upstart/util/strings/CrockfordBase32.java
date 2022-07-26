package upstart.util.strings;

import com.google.common.io.BaseEncoding;
import com.google.common.io.CrockfordBase32Encoding;

/**
 * <p>Provides Base32 encoding and decoding as defined by <a href="http://www.ietf.org/rfc/rfc4648.txt">RFC 4648</a>.
 * However, it uses a custom alphabet first coined by Douglas Crockford. The alphabet is further adjusted to allow
 * 'u' and 'U' characters to be decoded as if they were 'V' to correct mistakes by human input.<p/>
 *
 * <p>The alphabet is:<br/>0123456789ABCDEFGHJKMNPQRSTVWXYZ</p>
 * @see <a href="http://www.ietf.org/rfc/rfc4648.txt">RFC 4648</a>
 * @see <a href="http://www.crockford.com/wrmg/base32.html">Douglas Crockford's Base32 Encoding</a>
 */
public class CrockfordBase32 {
  public static final String UPPERCASE_ALPHABET = CrockfordBase32Encoding.UPPERCASE_ALPHABET;
  public static final String LOWERCASE_ALPHABET = CrockfordBase32Encoding.LOWERCASE_ALPHABET;

  public static CrockfordBase32Encoding lowerCaseInstance() {
    return CrockfordBase32Encoding.lowerCaseInstance();
  }

  public static CrockfordBase32Encoding upperCaseInstance() {
    return CrockfordBase32Encoding.upperCaseInstance();
  }
}
