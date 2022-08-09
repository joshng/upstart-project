package io.upstartproject.avrocodec;

import upstart.util.concurrent.CompletableFutures;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A transient in-memory schema-repo, intended primarily for testing (but possibly suitable for production use where
 * integration with an external {@link SchemaRegistry} is unnecessary or undesirable)
 */
public class MemorySchemaRegistry implements SchemaRegistry {
  private SchemaListener schemaListener;

  @Override
  public CompletableFuture<Void> insert(List<? extends SchemaDescriptor> schemas) {
    for (SchemaDescriptor schema : schemas) {
      schemaListener.onSchemaAdded(schema);
    }
    return CompletableFutures.nullFuture();
  }

  @Override
  public CompletableFuture<?> delete(SchemaDescriptor schema) {
    return CompletableFutures.nullFuture();
  }

  @Override
  public CompletableFuture<Void> refresh() {
    return CompletableFutures.nullFuture();
  }

  @Override
  public CompletableFuture<?> startUp(SchemaListener schemaListener) {
    this.schemaListener = schemaListener;
    return CompletableFutures.nullFuture();
  }

  @Override
  public CompletableFuture<?> shutDown() {
    return CompletableFutures.nullFuture();
  }
}
