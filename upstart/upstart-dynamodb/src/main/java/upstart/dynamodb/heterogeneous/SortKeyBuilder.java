package upstart.dynamodb.heterogeneous;

import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import upstart.util.collect.PersistentMap;

import java.util.function.Consumer;

import static upstart.dynamodb.heterogeneous.MixedDynamoTableReader.PK_ATTR;
import static upstart.dynamodb.heterogeneous.MixedDynamoTableReader.SK_EXPRESSION_ATTR;

public interface SortKeyBuilder<S> {
  PersistentMap<String, String> DUAL_KEY_ATTR_NAMES = PK_ATTR.attrNameMap().plusAll(SK_EXPRESSION_ATTR.attrNameMap());

  Key.Builder applySortKey(Key.Builder keyBuilder, S sortKeyValue);

  String prefixedSortKey(S sortKeyValue);

  default Key key(String partitionKey, S sortKeyValue) {
    return applySortKey(Key.builder().partitionValue(partitionKey), sortKeyValue).build();
  }

  default Consumer<QueryRequest.Builder> dualKeyQueryBuilder(String primaryKey, S sortKeyValue, String sortKeyRelation) {
    return sortKeyQueryBuilder(dualKeyQueryExpression(sortKeyRelation), primaryKey, sortKeyValue);
  }

  default String dualKeyQueryExpression(String relation) {
    return MixedDynamoTableReader.PRIMARY_KEY_CONDITION + " and " + SK_EXPRESSION_ATTR.relationExpression(relation);
  }

  default Consumer<QueryRequest.Builder> sortKeyQueryBuilder(String expression, String primaryKey, S sortKeyValue) {
    return builder -> builder.keyConditionExpression(expression)
            .expressionAttributeNames(DUAL_KEY_ATTR_NAMES)
            .expressionAttributeValues(dualKeyAttributeValues(primaryKey, sortKeyValue));
  }

  default PersistentMap<String, AttributeValue> dualKeyAttributeValues(String primaryKey, S sortKeyValue) {
    return PK_ATTR.expressionValue(primaryKey)
            .plusAll(SK_EXPRESSION_ATTR.expressionValue(prefixedSortKey(sortKeyValue)));
  }
}
