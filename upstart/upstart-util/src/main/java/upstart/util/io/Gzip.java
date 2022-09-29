package upstart.util.io;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import upstart.util.exceptions.UncheckedIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * User: josh
 * Date: Jan 27, 2011
 * Time: 6:49:26 PM
 */
public final class Gzip {
  public enum Compression {
    Faster(Deflater.BEST_SPEED),
    Smaller(Deflater.BEST_COMPRESSION);

    private final int level;

    Compression(int level) {
      this.level = level;
    }

    public int getLevel() {
      return level;
    }
  }

  public static byte[] decompress(byte[] compressedBytes) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteStreams.copy(decompressionStream(compressedBytes), outputStream);
    return outputStream.toByteArray();
  }

  public static byte[] compress(byte[] content) throws IOException {
    return compress(content, Compression.Faster);
  }

  public static byte[] compress(byte[] content, Compression compression) throws IOException {
    MemoryCompressionStream stream = newMemoryCompressionStream(compression);
    ByteStreams.copy(new ByteArrayInputStream(content), stream);
    return stream.toByteArray();
  }

  public static byte[] compress(String content, Charset charset) throws IOException {
    return compress(content.getBytes(charset));
  }

  public static String decompressString(byte[] compressedBytes, Charset charset) throws IOException {
    return new String(decompress(compressedBytes), charset);
  }

  public static String decompressUtf8String(byte[] compressedBytes) throws IOException {
    return decompressString(compressedBytes, Charsets.UTF_8);
  }

  public static String decompressUtf8String_unchecked(byte[] compressedBytes) {
    return UncheckedIO.getUnchecked(() -> decompressUtf8String(compressedBytes));
  }

  public static InputStream decompressionStream(byte[] compressedBytes) throws IOException {
    return decompressionStream(ByteSource.wrap(compressedBytes));
  }

  public static InputStream decompressionStream(Path path) throws IOException {
    return decompressionStream(MoreFiles.asByteSource(path));
  }

  public static InputStream decompressionStream(ByteSource source) throws IOException {
    return decompressionStream(source.openBufferedStream());
  }

  public static InputStream decompressionStream(InputStream byteStream) throws IOException {
    return new GZIPInputStream(byteStream);
  }

  public static MemoryCompressionStream newMemoryCompressionStream() throws IOException {
    return newMemoryCompressionStream(Compression.Faster);
  }

  public static MemoryCompressionStream newMemoryCompressionStream(Compression compression) throws IOException {
    return new MemoryCompressionStream(new ByteArrayOutputStream(), compression);
  }

  public static GZIPOutputStream compressionStream(Path path) throws IOException {
    return compressionStream(MoreFiles.asByteSink(path));
  }

  public static GZIPOutputStream compressionStream(OutputStream out) throws IOException {
    return new GZIPOutputStream(out);
  }

  public static GZIPOutputStream compressionStream(ByteSink sink) throws IOException {
    return compressionStream(sink.openBufferedStream());
  }

  public static class MemoryCompressionStream extends GZIPOutputStream {
    private final ByteArrayOutputStream byteStream;

    private MemoryCompressionStream(ByteArrayOutputStream out, Compression compression) throws IOException {
      super(out);
      def.setLevel(compression.getLevel());
      byteStream = out;
    }

    public byte[] toByteArray() throws IOException {
      close();
      return byteStream.toByteArray();
    }

    public void writeTo(OutputStream out) throws IOException {
      close();
      byteStream.writeTo(out);
    }
  }

  private Gzip() {
  }
}
