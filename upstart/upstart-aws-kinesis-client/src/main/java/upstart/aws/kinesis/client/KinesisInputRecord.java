package upstart.aws.kinesis.client;

import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.retrieval.KinesisClientRecord;
import upstart.util.concurrent.Promise;

import java.nio.ByteBuffer;

public class KinesisInputRecord {
  private final ProcessRecordsInput inputBatch;
  private final KinesisClientRecord record;
  private final Promise<Void> recordCompletionPromise;


  KinesisInputRecord(
          KinesisClientRecord record,
          ProcessRecordsInput inputBatch,
          Promise<Void> recordCompletionPromise
  ) {
    this.record = record;
    this.inputBatch = inputBatch;
    this.recordCompletionPromise = recordCompletionPromise;
  }

  public ProcessRecordsInput inputBatch() {
    return inputBatch;
  }

  public KinesisClientRecord record() {
    return record;
  }

  public void markComplete() {
    recordCompletionPromise.complete(null);
  }

  public void markFailed(Throwable t) {
    recordCompletionPromise.completeExceptionally(t);
  }

  public ByteBuffer data() {
    return record.data();
  }
}
