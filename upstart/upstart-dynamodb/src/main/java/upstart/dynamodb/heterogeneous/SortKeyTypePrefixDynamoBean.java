package upstart.dynamodb.heterogeneous;

public interface SortKeyTypePrefixDynamoBean extends MixedTableDynamoBean {
  String sortKeySuffix();

  default String sortKey() {
    return SortKeyTypePrefixExtractor.SORT_KEY_FORMAT.formatted(mixedTableTypeId(), sortKeySuffix());
  }

  default String strippedSortKeySuffix() {
    String sortKey = getSortKey();
    return sortKey.substring(sortKey.indexOf('#', 1) + 1);
  }
}
