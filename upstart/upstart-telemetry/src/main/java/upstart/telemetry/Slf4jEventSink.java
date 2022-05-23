package upstart.telemetry;

import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.events.PackagedEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.log.UpstartLogProvider;
import upstart.util.LogLevel;
import upstart.util.concurrent.CompletableFutures;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class Slf4jEventSink implements PackagedEventSink {
  private static final Logger LOG = LoggerFactory.getLogger(Slf4jEventSink.class);
  private final UpstartLogProvider logProvider;

  @Inject
  public Slf4jEventSink(UpstartLogProvider logProvider) {
    this.logProvider = logProvider;
  }

  @Override
  public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, PackagedEvent event, byte[] avroSerialized) {
    logProvider.logWithPayload(LOG, diagnosticLogLevel, event, "Event");
    return CompletableFutures.nullFuture();
  }

  @Override
  public void flush() {
  }
}
