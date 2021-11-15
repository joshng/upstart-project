package io.upstartproject.avrocodec;

import com.google.common.io.CountingOutputStream;
import com.google.common.primitives.Ints;
import io.upstartproject.hojack.Size;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.Scheduler;
import upstart.util.concurrent.TempFileFactory;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificDatumWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import static com.google.common.base.Preconditions.checkState;
import static upstart.util.exceptions.Fallible.fallible;


public abstract class AvroPackedLogFileAccumulator<R, S extends GenericRecord> {
  private final TempFileFactory tempFileFactory;
  private final Scheduler scheduler;
  private final AccumulatorConfig config;
  private final int blockSizeBytes;
  private final long targetFileSizeBytes;
  private final Class<S> serializedRecordClass;
  private final Schema serializedSchema;
  private LogFile currentFile;
  private boolean closed = false;

  public AvroPackedLogFileAccumulator(Class<S> serializedRecordClass, Schema serializedSchema, TempFileFactory tempFileFactory, Scheduler scheduler, AccumulatorConfig config) {
    this.serializedRecordClass = serializedRecordClass;
    this.serializedSchema = serializedSchema;
    this.tempFileFactory = tempFileFactory;
    this.scheduler = scheduler;
    this.config = config;
    blockSizeBytes = Ints.checkedCast(config.targetFileSize().toBytes());
    targetFileSizeBytes = config.uncompressedAvroBlockSize().toBytes();
  }

  protected abstract S pack(R record);
  protected abstract Path getOutputFileName(Instant openTimestamp);

  protected abstract void onRecordFailed(R badRecord, Path intendedFile, Throwable e);

  protected abstract void onFileFailed(Path path, long recordCount, Throwable e);

  protected abstract void onBytesWritten(long byteCount);

  protected abstract void onFileSealed(Path path, long recordCount);

  protected abstract <T> CompletableFuture<T> callSequentially(Callable<T> task);

  /**
   * @return a {@link CompletableFuture} which returns when the file holding this record has been <strong>closed</strong>.
   */
  public CompletableFuture<Path> append(R record) {
    S packedRecord = pack(record);
    return CompletableFutures.sequence(callSequentially(() -> {
      checkState(!closed, "%s was closed", this);
      LogFile openFile = currentFile;
      if (openFile == null || openFile.closeIfExpired()) currentFile = openFile = new LogFile();
      if (openFile.writeAndCloseIfFull(record, packedRecord)) currentFile = null;
      return openFile;
    }));
  }

  public CompletableFuture<Void> flush() {
    return callSequentially(fallible(this::closeCurrentFile));
  }

  public CompletableFuture<Void> close() {
    return callSequentially(fallible(() -> {
      closed = true;
      closeCurrentFile();
    }));
  }

  private void closeCurrentFile() {
    LogFile file = currentFile;
    if (file != null) {
      currentFile = null;
      file.close();
    }
  }

  class LogFile extends Promise<Path> {
    private final Instant fileExpiry;
    private final TempFileFactory.AtomicFileWriter fileWriter;
    private final CountingOutputStream byteCounter;
    private final DataFileWriter<S> output;
    private final ScheduledFuture<?> timeoutFuture;
    private final Path finalFileLocation;
    private boolean closed = false;
    private long bytesWritten = 0;
    private long appendedRecordCount = 0;

    public LogFile() throws IOException {
      Instant now = scheduler.now();
      finalFileLocation = getOutputFileName(now);
      fileWriter = tempFileFactory.atomicWriter(finalFileLocation);
      fileExpiry = now.plus(config.maxEmissionDelay());
      byteCounter = new CountingOutputStream(Files.newOutputStream(fileWriter.tempFile()));
      output = new DataFileWriter<>(new SpecificDatumWriter<>(serializedRecordClass))
              .setCodec(config.avroCompression())
              .setSyncInterval(blockSizeBytes)
              .create(serializedSchema, byteCounter);
      output.setFlushOnEveryBlock(true);
      timeoutFuture = scheduler.schedule(config.maxEmissionDelay(), () -> callSequentially(fallible(this::onExpiry)));
      whenComplete((path, e) -> {
        try {
          fileWriter.close();
        } catch (IOException closeException) {
          if (e != null) {
            e.addSuppressed(closeException);
          } else {
            e = closeException;
          }
        }
        if (e != null) onFileFailed(finalFileLocation, appendedRecordCount, e);
      });
    }

    boolean writeAndCloseIfFull(R record, S packedRecord) {
      appendedRecordCount++;
      boolean closed;
      try {
        output.append(packedRecord);
        long newByteCount = byteCounter.getCount();
        if (newByteCount > bytesWritten) {
          onBytesWritten(newByteCount - bytesWritten);
          bytesWritten = newByteCount;
          closed = newByteCount >= targetFileSizeBytes;
          if (closed) close();
        } else {
          closed = false;
        }
      } catch (Exception e) {
        closed = !(e instanceof DataFileWriter.AppendWriteException); // AppendWriteException means we can continue with other records
        if (closed) {
          try {
            output.close();
          } catch (IOException closeException) {
            e.addSuppressed(closeException);
          } finally {
            completeExceptionally(e);
          }
        } else {
          onRecordFailed(record, finalFileLocation, e);
        }
      }
      return closed;
    }

    boolean closeIfExpired() {
      boolean expired = !fileExpiry.isAfter(scheduler.now());
      if (expired) close();
      return expired;
    }

    void close() {
      checkState(!closed, "TextEntityFile.close called more than once");
      closed = true;
      timeoutFuture.cancel(false);
      tryComplete(() -> {
        output.close();
        fileWriter.commit();
        onFileSealed(finalFileLocation, appendedRecordCount);
        return finalFileLocation;
      });
    }

    private void onExpiry() {
      if (currentFile == this) {
        currentFile = null;
        close();
      }
    }
  }

  public interface AccumulatorConfig {
    Duration maxEmissionDelay();
    Size uncompressedAvroBlockSize();
    Size targetFileSize();

    default CodecFactory avroCompression() {
      return CodecFactory.snappyCodec();
    }
  }
}
