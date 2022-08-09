package io.upstartproject.avrocodec.events;

import upstart.util.LogLevel;

import java.util.concurrent.CompletableFuture;

public interface PackagedEventSink {
  CompletableFuture<?> publish(LogLevel diagnosticLogLevel, PackagedEvent event);

  void flush();
}
