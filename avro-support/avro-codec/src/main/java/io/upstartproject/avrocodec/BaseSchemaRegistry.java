package io.upstartproject.avrocodec;

import org.apache.avro.Schema;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class BaseSchemaRegistry implements SchemaRegistry {
  protected SchemaListener schemaListener;

  protected CompletableFuture<?> runOnDedicatedThread(String state, Runnable runnable) {
    return CompletableFuture.runAsync(runnable, task -> new Thread(task, getClass().getSimpleName() + "[" + state + "]").start());
  }

  protected <T> CompletableFuture<T> supplyOnDedicatedThread(String state, Supplier<T> runnable) {
    return CompletableFuture.supplyAsync(runnable, task -> new Thread(task, getClass().getSimpleName() + "[" + state + "]").start());
  }

  @Override
  public final CompletableFuture<?> startUp(SchemaListener schemaListener) {
    this.schemaListener = schemaListener;
    return startUpAsync();
  }

  protected abstract CompletableFuture<?> startUpAsync();

  protected void notifySchemaRemoved(long fingerprint) {
    notifySchemaRemoved(SchemaFingerprint.of(fingerprint));
  }

  protected void notifySchemaRemoved(SchemaFingerprint fingerprint) {
    schemaListener.onSchemaRemoved(fingerprint);
  }

  protected void notifySchemaAdded(String schemaJson) {
    notifySchemaAdded(SchemaDescriptor.of(schemaJson));
  }

  protected void notifySchemaAdded(InputStream schemaJsonStream) {
    notifySchemaAdded(SchemaDescriptor.from(schemaJsonStream));
  }

  protected void notifySchemaAdded(Schema schema) {
    notifySchemaAdded(SchemaDescriptor.of(schema));
  }

  protected void notifySchemaAdded(SchemaDescriptor descriptor) {
    schemaListener.onSchemaAdded(descriptor);
  }
}
