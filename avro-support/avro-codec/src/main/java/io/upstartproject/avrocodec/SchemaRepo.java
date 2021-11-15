package io.upstartproject.avrocodec;

import org.apache.avro.Schema;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An abstract "watchable" store for holding avro {@link Schema} definitions, keyed by their {@link SchemaDescriptor#fingerprint()}.
 * <p/>
 * Implementations must behave as follows:
 * <ol>
 *   <li>
 *     On {@link #startUp}, a background process should begin streaming {@link AvroCodec.SchemaDescriptorImpl}
 *     instances for every stored schema into the provided {@link SchemaListener#onSchemaAdded} callback.<br/>
 *     Each schema in the repo must be passed to {@link SchemaListener#onSchemaAdded onSchemaAdded} in sequence
 *     (eg, by a single thread), strictly in the order that they were inserted into the repo.<br/>
 *     The returned {@link CompletableFuture} should complete when the process has been successfully <em>started</em>.
 *   </li>
 *   <li>
 *     The repo must maintain the exact <em>globally-consistent</em> order that each schema was added, so that they can
 *     be presented to the {@link SchemaListener} in the correct order.
*    </li>
 *   <li>
 *     In response to {@link #refresh}, the up-to-date contents of the repo must be determined. The CompletableFuture
 *     returned from {@link #refresh} must complete only after all previously-acknowledged schema additions/deletions
 *     have been presented to the {@link SchemaListener}.
 *   </li>
 * </ol>
 * @see SchemaDescriptor#from
 * @see AvroCodec#AvroCodec(SchemaRepo)
 */
public interface SchemaRepo {
  /**
   * Must begin a background process which streams all schema additions and removals in this {@link SchemaRepo} to the
   * provided {@link SchemaListener}
   *
   * @return a {@link CompletableFuture} that completes when the background process has been successfully started
   */
  CompletableFuture<?> startUp(SchemaListener schemaListener);

  /**
   * Must insert the JSON {@link SchemaDescriptor#schema} from each of the provided
   * {@link AvroCodec.SchemaDescriptorImpl SchemaDescriptors} into the store, keyed by their {@link AvroCodec.SchemaDescriptorImpl#fingerprint}.
   * <p/>
   * <em>IMPORTANT:</em> Note that the inserted schemas must eventually also be passed back to {@link SchemaListener#onSchemaAdded},
   * according to the order they were added to the backing store.
   * <p/>
   * Also note: if the returned {@link CompletableFuture} fails (ie, completes <em>exceptionally</em>), then the calling
   * {@link AvroCodec} will <strong>FAIL</strong>, resulting in exceptions being thrown for all subsequent interactions.
   *
   * @return a {@link CompletableFuture} which completes only after all of the provided schemas have been acknowledged
   * as persisted in the repo (although perhaps before they have been surfaced to the {@link SchemaListener})
   * @param schemas
   */
  CompletableFuture<?> insert(List<? extends SchemaDescriptor> schemas);

  /**
   * Must delete the indicated schema from the repo.
   *
   * @return a {@link CompletableFuture} which completes only when the provided schema has been been acknowledged
   * deleted (but perhaps before the deletion has been reported to the {@link SchemaListener})
   */
  CompletableFuture<?> delete(SchemaDescriptor schema);

  /**
   * Must return a {@link CompletableFuture} which completes only when the {@link SchemaListener} provided to
   * {@link #startUp} has witnessed all {@link Schema} additions and removals which were performed in this
   * {@link SchemaRepo} beforehand.
   */
  CompletableFuture<Void> refresh();

  /**
   * Stops all background processes and closes all connections
   */
  CompletableFuture<?> shutDown();

  interface SchemaListener {
    void onSchemaAdded(SchemaDescriptor schema);
    void onSchemaRemoved(SchemaFingerprint fingerprint);
  }
}
