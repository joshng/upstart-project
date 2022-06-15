package upstart.util.strings;

import com.google.common.io.BaseEncoding;

import java.nio.ByteBuffer;
import java.util.UUID;

public class RandomId {
  private static final int UUID_BYTE_LENGTH = 2 * Long.BYTES;
  private static final BaseEncoding RANDOM_ID_ENCODING = BaseEncoding.base64Url().omitPadding();

  private RandomId() { }

  public static String newRandomId() {
    return RANDOM_ID_ENCODING.encode(toBytes(UUID.randomUUID()));
//    return new CrockfordBase32(false).encodeToString(toBytes(UUID.randomUUID()));
  }

  private static byte[] toBytes(UUID identifier) {
    return putBytes(ByteBuffer.allocate(UUID_BYTE_LENGTH), identifier).array();
  }

  private static ByteBuffer putBytes(ByteBuffer buf, UUID identifier) {
    return buf.putLong(identifier.getMostSignificantBits())
            .putLong(identifier.getLeastSignificantBits());
  }
}
