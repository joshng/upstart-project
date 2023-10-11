package com.google.common.hash;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class CompositeHashFunction extends AbstractCompositeHashFunction {
  private final int bits;

  public static CompositeHashFunction of(HashFunction... functions) {
    return new CompositeHashFunction(functions);
  }

  public CompositeHashFunction(HashFunction... functions) {
    super(functions);
    this.bits = Arrays.stream(functions).mapToInt(HashFunction::bits).sum();
  }

  public List<HashCode> getComponentHashes(HashCode compositeHashCode) {
    if (compositeHashCode instanceof CompositeHashCode composite) return composite.components();
    throw new IllegalArgumentException("Expected CompositeHashCode");
  }

  @Override
  CompositeHashCode makeHash(Hasher[] hashers) {
    return new CompositeHashCode(Arrays.stream(hashers).map(Hasher::hash).toList());
  }

  @Override
  public int bits() {
    return bits;
  }

  // mathy bits copied from Hashing.BytesHashCode
  public static final class CompositeHashCode extends HashCode implements Serializable {
    private final List<HashCode> components;
    private byte[] bytesCache;

    CompositeHashCode(List<HashCode> components) {
      this.components = components;
    }

    public List<HashCode> components() {
      return components;
    }

    @Override
    public int bits() {
      return components.stream().mapToInt(HashCode::bits).sum();
    }

    @Override
    public byte[] asBytes() {
      return getBytesInternal().clone();
    }

    @Override
    public int asInt() {
      byte[] bytes = getBytesInternal();
      checkState(
              bytes.length >= 4,
              "HashCode#asInt() requires >= 4 bytes (it only has %s bytes).",
              bytes.length);
      return (bytes[0] & 0xFF)
              | ((bytes[1] & 0xFF) << 8)
              | ((bytes[2] & 0xFF) << 16)
              | ((bytes[3] & 0xFF) << 24);
    }

    @Override
    public long asLong() {
      byte[] bytes = getBytesInternal();
      checkState(
              bytes.length >= 8,
              "HashCode#asLong() requires >= 8 bytes (it only has %s bytes).",
              bytes.length);
      return padToLong();
    }

    @Override
    public long padToLong() {
      byte[] bytes = getBytesInternal();
      long retVal = (bytes[0] & 0xFF);
      for (int i = 1; i < Math.min(bytes.length, 8); i++) {
        retVal |= (bytes[i] & 0xFFL) << (i * 8);
      }
      return retVal;
    }

    @Override
    void writeBytesToImpl(byte[] dest, int offset, int maxLength) {
      System.arraycopy(asBytes(), 0, dest, offset, maxLength);
    }

    @Override
    byte[] getBytesInternal() {
      if (bytesCache == null) {
        byte[] b = new byte[bits() / 8];
        int i = 0;
        for (HashCode newHash : components) {
          i += newHash.writeBytesTo(b, i, newHash.bits() / 8);
        }
        bytesCache = b;
      }
      return bytesCache;
    }

    @Override
    boolean equalsSameBits(HashCode that) {
      return bits() == that.bits() && Arrays.equals(getBytesInternal(), that.getBytesInternal());
    }
  }
}
