package upstart.util.strings;

import com.google.common.io.BaseEncoding;

import java.nio.ByteBuffer;
import java.util.UUID;

public class RandomId {
  private static final int UUID_BYTE_LENGTH = 2 * Long.BYTES;
  private static final BaseEncoding RANDOM_ID_ENCODING = CrockfordBase32.lowerCaseInstance();

  private RandomId() { }

  public static String newRandomId() {
    return newRandomId(RANDOM_ID_ENCODING);
  }

  public static String newRandomId(BaseEncoding encoding) {
    return encoding.encode(newRandomBytes());
  }

  public static byte[] newRandomBytes() {
    return toBytes(UUID.randomUUID());
  }

  public static byte[] toBytes(UUID identifier) {
    return putBytes(ByteBuffer.allocate(UUID_BYTE_LENGTH), identifier).array();
  }

  private static ByteBuffer putBytes(ByteBuffer buf, UUID identifier) {
    return buf.putLong(identifier.getMostSignificantBits())
            .putLong(identifier.getLeastSignificantBits());
  }
}
