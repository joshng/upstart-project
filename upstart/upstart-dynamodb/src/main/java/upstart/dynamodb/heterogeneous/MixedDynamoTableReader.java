package upstart.dynamodb.heterogeneous;

import com.google.common.collect.ImmutableMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import upstart.dynamodb.DynamoTable;
import upstart.dynamodb.DynamoTableMapper;
import upstart.dynamodb.DynamoTableDao;
import upstart.dynamodb.DynamoTableReader;
import upstart.util.concurrent.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class MixedDynamoTableReader<B extends MixedTableDynamoBean, T> implements DynamoTableMapper {
  public static final DynamoTableReader.ExpressionAttribute PK_ATTR = DynamoTableReader.ExpressionAttribute.of(MixedTableDynamoBean.PARTITION_KEY_ATTRIBUTE);
  // sort key expression attribute
  public static final DynamoTableReader.ExpressionAttribute SK_EXPRESSION_ATTR = DynamoTableReader.ExpressionAttribute.of(MixedTableDynamoBean.SORT_KEY_ATTRIBUTE);
  public static final String PRIMARY_KEY_CONDITION = PK_ATTR.equalityExpression();
  private final Class<? super B> baseBeanClass;
  private final TypeIdExtractor<? super B> discriminator;
  private final DynamoTable table;
  private final Map<String, DynamoTableReader<? extends B, ? extends T>> readersByTypeId;
  private final Consumer<QueryRequest.Builder> defaultQuery;
  private final DynamoDbAsyncClient client;

  @SafeVarargs
  protected MixedDynamoTableReader(
          Class<? super B> baseBeanClass,
          TypeIdExtractor<? super B> discriminator,
          DynamoTable table,
          DynamoTableReader<? extends B, ? extends T>... specificTypeReaders
  ) {
    this(baseBeanClass, discriminator, table, Set.of(specificTypeReaders));
  }

  protected MixedDynamoTableReader(
          Class<? super B> baseBeanClass,
          TypeIdExtractor<? super B> discriminator,
          DynamoTable table,
          Set<DynamoTableReader<? extends B, ? extends T>> specificTypeReaders
  ) {
    this.table = table;
    this.client = table.client();
    this.baseBeanClass = baseBeanClass;
    this.discriminator = discriminator;
    readersByTypeId = specificTypeReaders.stream().collect(ImmutableMap.toImmutableMap(
            reader -> MixedTableDynamoBean.TYPE_IDS.get(reader.beanClass()),
            Function.identity()
    ));
    defaultQuery = b -> b.tableName(tableName());
    var mismatchedReaders = specificTypeReaders.stream().filter(r -> r.dao().table() != table).toList();
    checkState(mismatchedReaders.isEmpty(), "Table mismatch for readers, expected all to reference table %s: %s", tableName(), mismatchedReaders);
    table.registerApi(this);
  }

  public String tableName() {
    return table.tableName();
  }

  public Flux<T> primaryKeyQuery(String pk) {
    return primaryKeyQuery(pk, b -> {});
  }

  public Flux<T> primaryKeyQuery(String pk, Consumer<QueryRequest.Builder> queryRequest) {
    return query(b -> b
            .applyMutation(primaryKeyQueryBuilder(pk))
            .applyMutation(queryRequest)
    );
  }

  protected static Consumer<QueryRequest.Builder> primaryKeyQueryBuilder(String pk) {
    return b -> b
            .keyConditionExpression(PRIMARY_KEY_CONDITION)
            .expressionAttributeNames(PK_ATTR.attrNameMap())
            .expressionAttributeValues(PK_ATTR.expressionValue(pk));
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

  @Override
  public Class<?> beanClass() {
    return baseBeanClass;
  }

  @Override
  public CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
    return builder;
  }

  @Override
  public TableSchema<?> tableSchema() {
    return DynamoTableDao.getTableSchema(baseBeanClass);
  }

  @Override
  public Optional<String> getTtlAttribute() {
    return Optional.empty(); // any TTL attribute on the baseBeanClass will be obtained via each subclass-reader
  }

  private Mono<? extends T> unpack(Map<String, AttributeValue> fields) {
    String typeId = discriminator.extractTypeId(fields);
    DynamoTableReader<? extends B, ? extends T> reader = readersByTypeId.get(typeId);
    checkArgument(reader != null, "No reader for type '%s' in table '%s'", typeId, tableName());
    return Mono.fromFuture(() -> mapToItem(fields, reader));
  }

  private static <B extends MixedTableDynamoBean, T> Promise<? extends T> mapToItem(
          Map<String, AttributeValue> fields,
          DynamoTableReader<B, T> reader
  ) {
    return reader.transform(reader.tableSchema().mapToItem(fields));
  }

  public interface TypeIdExtractor<B extends MixedTableDynamoBean> {
    String extractTypeId(Map<String, AttributeValue> data);
  }
}
