package upstart.dynamodb.heterogeneous;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import static com.google.common.base.Preconditions.checkState;

public interface TypeAttributeDynamoBean extends MixedTableDynamoBean {
  String TYPE_ATTRIBUTE = "_T";

  @DynamoDbAttribute(TYPE_ATTRIBUTE)
  default String getTypeId() {
    return mixedTableTypeId();
  }

  default void setTypeId(String typeId) {
    String expected = getTypeId();
    checkState(expected.equals(typeId), "Type ID mismatch: expected %s, not %s", expected, typeId);
  }
}
