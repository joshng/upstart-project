package upstart.dynamodb.heterogeneous;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import static com.google.common.base.Preconditions.checkState;

public interface MixedTableDynamoBean {
  ClassValue<String> TYPE_IDS = new ClassValue<>() {
    @Override
    protected String computeValue(Class<?> type) {
      DynamoTypeId anno = type.getAnnotation(DynamoTypeId.class);
      checkState(anno != null, "Missing @%s annotation on %s", DynamoTypeId.class.getSimpleName(), type);
      return anno.value();
    }
  };
  String SORT_KEY_ATTRIBUTE = "SK";
  String PARTITION_KEY_ATTRIBUTE = "PK";

  String mixedTableTypeId();

  String partitionKey();

  String sortKey();

  static String typeId(Class<? extends MixedTableDynamoBean> beanClass) {
    return TYPE_IDS.get(beanClass);
  }

  @DynamoDbAttribute(PARTITION_KEY_ATTRIBUTE)
  @DynamoDbPartitionKey
  default String getPartitionKey() {
    return partitionKey();
  }

  void setPartitionKey(String partitionKey);

  @DynamoDbAttribute(SORT_KEY_ATTRIBUTE)
  @DynamoDbSortKey
  default String getSortKey() {
    return sortKey();
  }

  void setSortKey(String sortKey);

  abstract class Base implements MixedTableDynamoBean {

    private String typeId;
    private String partitionKey;
    private String sortKey;

    @Override
    public String mixedTableTypeId() {
      String id = typeId;
      if (id == null) {
        typeId = id = typeId(getClass());
      }
      return id;
    }

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
}
