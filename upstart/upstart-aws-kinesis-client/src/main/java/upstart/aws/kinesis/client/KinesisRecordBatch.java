package upstart.aws.kinesis.client;

import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class KinesisRecordBatch {
  private final ProcessRecordsInput input;
  private final List<KinesisInputRecord> records;

  public KinesisRecordBatch(ProcessRecordsInput input, List<KinesisInputRecord> records) {
    this.input = input;
    this.records = records;
  }

  public Instant cacheEntryTime() {
    return input.cacheEntryTime();
  }

  public Instant cacheExitTime() {
    return input.cacheExitTime();
  }

  public boolean isAtShardEnd() {
    return input.isAtShardEnd();
  }

  public List<KinesisInputRecord> records() {
    return records;
  }

  public long millisBehindLatest() {
    return input.millisBehindLatest();
  }

  public Duration timeSpentInCache() {
    return input.timeSpentInCache();
  }

}
