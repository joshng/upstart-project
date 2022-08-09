package upstart.telemetry;

import io.upstartproject.avrocodec.MessageMetadata;
import upstart.config.UpstartModule;
import upstart.config.annotations.ConfigPath;

public class MessageEnvelopeModule extends UpstartModule {
  @Override
  protected void configure() {
    MessageEnvelopeConfig config = bindConfig(MessageEnvelopeConfig.class);
    bind(MessageMetadata.class).toInstance(config.metadata());
  }

  @ConfigPath("upstart.messageEnvelope")
  public interface MessageEnvelopeConfig {
    MessageMetadata metadata();
  }
}
