package upstart.dynamodb.heterogeneous;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class TypeAttributeExtractor implements MixedDynamoTableReader.TypeIdExtractor<TypeAttributeDynamoBean> {
  @Override
  public String extractTypeId(Map<String, AttributeValue> data) {
    return data.get(TypeAttributeDynamoBean.TYPE_ATTRIBUTE).s();
  }
}
