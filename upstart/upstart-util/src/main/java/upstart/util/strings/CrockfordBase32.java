package upstart.util.strings;

import com.google.common.io.BaseEncoding;
import com.google.common.io.CrockfordBase32Encoding;

/**
 * <p>Provides Base32 encoding and decoding as defined by <a href="http://www.ietf.org/rfc/rfc4648.txt">RFC 4648</a>.
 * However it uses a custom alphabet first coined by Douglas Crockford. Only addition to the alphabet is that 'u' and
 * 'U' characters decode as if they were 'V' to improve mistakes by human input.<p/>
 * <p>
 * This class operates directly on byte streams, and not character streams.
 * </p>
 *
 * @version $Id: Base32.java 1382498 2012-09-09 13:41:55Z sebb $
 * @see <a href="http://www.ietf.org/rfc/rfc4648.txt">RFC 4648</a>
 * @see <a href="http://www.crockford.com/wrmg/base32.html">Douglas Crockford's Base32 Encoding</a>
 * @since 1.5
 */
public class CrockfordBase32 {
  public static final String UPPERCASE_ALPHABET = CrockfordBase32Encoding.UPPERCASE_ALPHABET;
  public static final String LOWERCASE_ALPHABET = CrockfordBase32Encoding.LOWERCASE_ALPHABET;

  public static BaseEncoding lowerCaseInstance() {
    return CrockfordBase32Encoding.lowerCaseInstance();
  }
}
