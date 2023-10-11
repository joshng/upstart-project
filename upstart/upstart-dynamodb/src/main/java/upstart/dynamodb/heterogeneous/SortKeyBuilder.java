package upstart.dynamodb.heterogeneous;

import software.amazon.awssdk.enhanced.dynamodb.Key;

public interface SortKeyBuilder<S> {
  default Key key(String partitionKey, S sortKeyValue) {
    return applySortKey(Key.builder().partitionValue(partitionKey), sortKeyValue).build();
  }

  Key.Builder applySortKey(Key.Builder keyBuilder, S sortKeyValue);

  String prefixedSortKey(S sortKeyValue);
}
