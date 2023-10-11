package upstart.util.io;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import upstart.util.concurrent.Promise;
import upstart.util.exceptions.ThrowingConsumer;
import upstart.util.exceptions.ThrowingSupplier;
import upstart.util.exceptions.Unchecked;
import upstart.util.exceptions.UncheckedIO;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkState;

public class AsyncFileReader<T> {
  public static final int DEFAULT_BUFFER_SIZE = 8192;
  private final int defaultBufferSize;
  private final Callable<? extends Processor<? extends T>> processorFactory;

  protected AsyncFileReader(int defaultBufferSize, Callable<? extends Processor<? extends T>> processorFactory) {
    this.defaultBufferSize = defaultBufferSize;
    this.processorFactory = processorFactory;
  }

  public AsyncFileReader(Callable<? extends Processor<? extends T>> processorFactory) {
    this(DEFAULT_BUFFER_SIZE, processorFactory);
  }

  public static AsyncFileReader<HashCode> hashing(HashFunction hashFunction) {
    return new AsyncFileReader<>(() -> {
      var hasher = hashFunction.newHasher();
      return processor(hasher::putBytes, hasher::hash);
    });
  }

  public static AsyncFileReader<byte[]> messageDigest(String messageDigest) {
    return new AsyncFileReader<>(() -> {
      var digest = Unchecked.getUnchecked(() -> MessageDigest.getInstance(messageDigest));
      return processor(digest::update, digest::digest);
    });
  }

  public static <T> Processor<T> processor(ThrowingConsumer<ByteBuffer> process, ThrowingSupplier<T> finish) {
    return new Processor<>() {
      @Override
      public void update(ByteBuffer bytes) throws Exception {
        process.acceptOrThrow(bytes);
      }

      @Override
      public T finish() throws Exception {
        return finish.getOrThrow();
      }
    };
  }

  public Promise<T> read(Path path) {
    return read(path, defaultBufferSize);
  }

  public Promise<T> read(Path path, int bufferSize) {
    return read(path, ByteBuffer.allocate((int) bufferSize));
  }

  public Promise<T> read(Path path, ByteBuffer buf) {
    return Promise.thatCompletes(promise -> {
      var processor = processorFactory.call();
      AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
      promise.whenComplete((result, error) -> UncheckedIO.runUnchecked(fileChannel::close));
      CompletionHandler<Integer, Promise<T>> completionHandler = new CompletionHandler<>() {
        private volatile long pos = 0;

        @Override
        public void completed(Integer bytesRead, Promise<T> attachment) {
          if (bytesRead == -1) {
            attachment.tryComplete(processor::finish);
            return;
          }
          checkState(bytesRead > 0);
          buf.flip();
          if (!attachment.consumeFailure(() -> processor.update(buf)).isDone()) {
            buf.clear();
            fileChannel.read(buf, pos += bytesRead, attachment, this);
          }
        }

        @Override
        public void failed(Throwable exc, Promise<T> attachment) {
          promise.completeExceptionally(exc);
        }
      };
      fileChannel.read(buf, 0, promise, completionHandler);
    });
  }

  public interface Processor<T> {
    void update(ByteBuffer bytes) throws Exception;

    T finish() throws Exception;
  }
}
