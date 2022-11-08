package io.upstartproject.avrocodec.upstart;

import com.google.inject.TypeLiteral;
import io.upstartproject.avro.PackedRecord;
import io.upstartproject.avrocodec.RecordTypeFamily;
import upstart.util.LogLevel;
import io.upstartproject.avrocodec.PackableRecord;
import io.upstartproject.avrocodec.RecordPackerApi;
import io.upstartproject.avrocodec.SchemaFingerprint;
import io.upstartproject.avrocodec.events.EventLog;
import io.upstartproject.avrocodec.events.PackagedEvent;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class EventLogger<T extends SpecificRecordBase> implements PackagedEvent.Decoratable<EventLogger<T>>, RecordPackerApi<T> {
  private final AvroPacker<T> packer;
  private final EventLog eventLog;

  @Inject
  public EventLogger(TypeLiteral<T> eventType, @DataStore("TELEMETRY") AvroPublicationModule.AvroPublicationService codecService, EventLog eventLog) {
    this.packer = codecService.packerFor(eventType);
    this.eventLog = eventLog;
  }

  public EventLogger(AvroPacker<T> packer, EventLog eventLog) {
    this.packer = packer;
    this.eventLog = eventLog;
  }

  public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, T eventRecord) {
    return eventLog.publishRecord(diagnosticLogLevel, makePackable(eventRecord));
  }

  public void unreliablePublishOrWarn(LogLevel logLevel, T eventRecord, Logger errorLogger, PackagedEvent.Decorator initializer) {
    eventLog.publish(logLevel, b -> initializer.decorate(b.event(makePackable(eventRecord))))
            .whenComplete((ignored, e) -> Optional.ofNullable(e).ifPresent(err -> errorLogger.warn(
                    "Error while logging event: {}{}",
                    eventRecord.getClass().getName(),
                    eventRecord,
                    e
            )));
  }

  public void unreliablePublishOrWarn(LogLevel logLevel, T eventRecord, Logger errorLogger) {
    publish(logLevel, eventRecord).whenComplete((ignored, e) -> {
      Optional.ofNullable(e).ifPresent(err -> errorLogger.warn("Error while logging event: {}{}", eventRecord.getClass().getName(), eventRecord, e));
    });
  }

  public EventLogger<T> withKeyStrategy(Function<T, String> keyStrategy) {
    return decorated((eventRecord, b) -> b.key(keyStrategy.apply(eventRecord)));
  }


  public EventLogger<T> decorated(BiFunction<T, PackagedEvent.Builder, PackagedEvent.Builder> initializer) {

    return new EventLogger<>(packer, eventLog) {
      @Override
      public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, T eventRecord) {
        return decorated(b -> initializer.apply(eventRecord, b)).publish(diagnosticLogLevel, eventRecord);
      }
    };
  }

  @Override
  public PackagedEvent.Decorator decorator() {
    return eventLog.decorator();
  }

  @Override
  public EventLogger<T> overrideDecorator(PackagedEvent.Decorator eventDecorator) {
    return new EventLogger<>(packer, eventLog.overrideDecorator(eventDecorator));
  }

  @Override
  public PackedRecord pack(T record) {
    return packer.pack(record);
  }

  @Override
  public RecordTypeFamily getTypeFamily() {
    return packer.getTypeFamily();
  }

  @Override
  public SchemaFingerprint fingerprint() {
    return packer.fingerprint();
  }

  @Override
  public Schema schema() {
    return packer.schema();
  }

  @Override
  public PackableRecord<? extends T> makePackable(T record) {
    return packer.makePackable(record);
  }

  @Override
  public String fullName() {
    return packer.fullName();
  }

  public EventLog eventLog() {
    return eventLog;
  }
}
