package com.google.common.hash;

import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

public class XXHash64Function extends AbstractHashFunction {
  public static final XXHash64Function INSTANCE = new XXHash64Function();
  private static final XXHashFactory FACTORY = XXHashFactory.fastestInstance();
  @Override
  public Hasher newHasher() {
    StreamingXXHash64 hash = FACTORY.newStreamingHash64(0);

    return new AbstractHasher() {
      @Override
      public Hasher putByte(byte b) {
        hash.update(new byte[] {b}, 0, 1);
        return this;
      }

      @Override
      public Hasher putBytes(byte[] bytes, int off, int len) {
        hash.update(bytes, off, len);
        return this;
      }

      @Override
      public HashCode hash() {
        return HashCode.fromLong(hash.getValue());
      }
    };
  }

  @Override
  public int bits() {
    return 64;
  }
}
