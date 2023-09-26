package upstart.dynamodb.heterogeneous;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import upstart.dynamodb.DynamoTable;
import upstart.dynamodb.DynamoTableReader;
import upstart.util.concurrent.Promise;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public abstract class MixedDynamoTableReader<B extends MixedDynamoTableReader.BaseItem, T> {
  private final TypeIdExtractor<? super B> discriminator;
  private String tableName;
  private DynamoDbAsyncClient client;
  private final Map<String, DynamoTableReader<? extends B, ? extends T>> readersByTypeId;
  private List<String> allAttributes;
  private Consumer<QueryRequest.Builder> defaultQuery;
  private String allAttributesProjectionExpression;

  protected MixedDynamoTableReader(
          TypeIdExtractor<? super B> discriminator,
          DynamoTable table,
          Collection<? extends DynamoTableReader<? extends B, ? extends T>> specificTypeReaders
  ) {
    this.discriminator = discriminator;
    table.addListener(new Service.Listener() {
      @Override
      public void running() {
        // confirm these readers all project from the same table via the same client
        tableName = specificTypeReaders.stream().map(reader -> reader.enhancedTable().tableName()).distinct().collect(MoreCollectors.onlyElement());
        checkState(table.tableName().equals(tableName), "Table name mismatch: expected %s, not %s", table.tableName(), tableName);
        client = table.client();
        allAttributes = specificTypeReaders.stream().flatMap(r -> r.enhancedTable().tableSchema().attributeNames().stream()).distinct().toList();
        allAttributesProjectionExpression = String.join(",", allAttributes);
        defaultQuery = b -> b
//                .keyConditionExpression("#pk = :pk")
//                .expressionAttributeNames(Map.of("#pk", BaseItem.SORT_KEY_ATTRIBUTE))
                .tableName(tableName)
                .projectionExpression(allAttributesProjectionExpression);
      }
    }, MoreExecutors.directExecutor());
    readersByTypeId = specificTypeReaders.stream().collect(ImmutableMap.toImmutableMap(
            reader -> BaseItem.TYPE_IDS.get(reader.dao().mappedClass()),
            Function.identity()
    ));
  }

  public Flux<T> query(Consumer<QueryRequest.Builder> queryRequest) {
    return Flux.from(client.queryPaginator(defaultQuery.andThen(queryRequest)))
            .flatMapIterable(QueryResponse::items)
            .flatMap(this::unpack);
  }

  public Flux<T> scan(Consumer<ScanRequest.Builder> queryRequest) {
    return Flux.from(client.scanPaginator(queryRequest))
            .flatMapIterable(ScanResponse::items)
            .flatMap(this::unpack);
  }

  private Mono<? extends T> unpack(Map<String, AttributeValue> fields) {
    String typeId = discriminator.extractTypeId(fields);
    DynamoTableReader<? extends B, ? extends T> reader = readersByTypeId.get(typeId);
    checkArgument(reader != null, "No reader for blob type '%s' in table '%s'", typeId, tableName);
    return Mono.fromFuture(() -> mapToItem(fields, reader));
  }

  private static <B extends BaseItem, T> Promise<? extends T> mapToItem(
          Map<String, AttributeValue> fields,
          DynamoTableReader<B, ? extends T> reader
  ) {
    return reader.unpack(reader.dao().tableSchema().mapToItem(fields));
  }

  public interface TypeIdExtractor<B extends BaseItem> {
    String extractTypeId(Map<String, AttributeValue> data);
  }


  public static abstract class BaseItem {
    private static final ClassValue<String> TYPE_IDS = new ClassValue<>() {
      @Override
      protected String computeValue(Class<?> type) {
        DynamoTypeId anno = type.getAnnotation(DynamoTypeId.class);
        checkState(anno != null, "Missing @%s annotation on %s", DynamoTypeId.class.getSimpleName(), type);
        return anno.value();
      }
    };

    public static final String SORT_KEY_ATTRIBUTE = "SK";
    public static final String PARTITION_KEY_ATTRIBUTE = "PK";
    private String typeId;
    private String partitionKey;
    private String sortKey;

    public String mixedTableTypeId() {
      String id = typeId;
      if (id == null) typeId = id = TYPE_IDS.get(getClass());
      return id;
    }

    public abstract String partitionKey();
    public abstract String sortKey();

    @DynamoDbAttribute(PARTITION_KEY_ATTRIBUTE)
    @DynamoDbPartitionKey
    public String getPartitionKey() {
      if (partitionKey == null) partitionKey = partitionKey();
      return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
      this.partitionKey = partitionKey;
    }

    @DynamoDbAttribute(SORT_KEY_ATTRIBUTE)
    @DynamoDbSortKey
    public String getSortKey() {
      if (sortKey == null) sortKey = sortKey();
      return sortKey;
    }

    public void setSortKey(String sortKey) {
      this.sortKey = sortKey;
    }
  }

  public static class SortKeyTypePrefixExtractor implements TypeIdExtractor<SortKeyTypePrefixItem> {

    public static final Pattern TYPE_PATTERN = Pattern.compile("^#(.+?)#");
    public static final String SORT_KEY_FORMAT = "#%s#%s";

    @Override
    public String extractTypeId(Map<String, AttributeValue> data) {
      String sortKey = data.get(BaseItem.SORT_KEY_ATTRIBUTE).s();
      Matcher matcher = TYPE_PATTERN.matcher(sortKey);
      checkState(matcher.find(), "No type discriminator found in sort key: %s", sortKey);
      return matcher.group(1);
    }
  }

  public abstract static class SortKeyTypePrefixItem extends BaseItem {

    @Override
    public String sortKey() {
      return SortKeyTypePrefixExtractor.SORT_KEY_FORMAT.formatted(mixedTableTypeId(), sortKeySuffix());
    }

    protected String strippedSortKeySuffix() {
      String sortKey = getSortKey();
      return sortKey.substring(sortKey.indexOf('#', 1) + 1);
    }

    protected abstract String sortKeySuffix();
  }

  public static class TypeAttributeExtractor implements TypeIdExtractor<TypeAttributeBlob> {
    @Override
    public String extractTypeId(Map<String, AttributeValue> data) {
      return data.get(TypeAttributeBlob.TYPE_ATTRIBUTE).s();
    }
  }

  public abstract static class TypeAttributeBlob extends BaseItem {
    public static final String TYPE_ATTRIBUTE = "_T";
    private String typeId;

    @Deprecated
    protected TypeAttributeBlob() {
    }

    @DynamoDbAttribute(TYPE_ATTRIBUTE)
    public final String getTypeId() {
      return mixedTableTypeId();
    }

    public final void setTypeId(String typeId) {
      String expected = getTypeId();
      checkState(expected.equals(typeId), "Type ID mismatch: expected %s, not %s", expected, typeId);
    }
  }
}
