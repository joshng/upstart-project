package upstart.aws.kinesis.client;

import software.amazon.kinesis.lifecycle.events.InitializationInput;


public interface KinesisShardProcessor {

  void initialize(InitializationInput initializationInput);

  void processRecord(KinesisInputRecord record) throws Exception;

  /**
   * Called when the Scheduler has been requested to shutdown. This is called while the record processor still holds
   * the lease so checkpointing is possible. Once this method has completed the lease for the record processor is
   * released, and {@link #leaseLost()} will be called at a later time.
   */
  void shutdownRequested() throws Exception;

  /**
   * Called when the lease that tied to this record processor has been lost. Once the lease has been lost the record
   * processor can no longer checkpoint.
   */
  void leaseLost();

}
