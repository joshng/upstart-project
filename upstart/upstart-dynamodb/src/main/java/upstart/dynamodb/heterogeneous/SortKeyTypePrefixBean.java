package upstart.dynamodb.heterogeneous;

import com.google.common.base.Converter;
import software.amazon.awssdk.enhanced.dynamodb.Key;

public interface SortKeyTypePrefixBean extends MixedTableDynamoBean {
  String sortKeySuffix();

  static String formatSortKey(String typeId, String sortKeySuffix) {
    return SortKeyTypePrefixExtractor.SORT_KEY_FORMAT.formatted(typeId, sortKeySuffix);
  }

  static <B extends SortKeyTypePrefixBean> KeyBuilder<B> keyBuilder(Class<B> beanClass) {
    return new KeyBuilder<>(beanClass);
  }
  static <S> SortKeyBuilder<S> keyBuilder(Class<? extends SortKeyTypePrefixBean> beanClass, Converter<S, String> converter) {
    return keyBuilder(beanClass).converted(converter);
  }

  default String sortKey() {
    return formatSortKey(mixedTableTypeId(), sortKeySuffix());
  }

  default String strippedSortKeySuffix() {
    String sortKey = getSortKey();
    return sortKey.substring(sortKey.indexOf('#', 1) + 1);
  }

  class KeyBuilder<B extends SortKeyTypePrefixBean> implements SortKeyBuilder<String> {
    private final String typeId;

    public KeyBuilder(Class<B> beanClass) {
      this.typeId = MixedTableDynamoBean.typeId(beanClass);
    }

    @Override
    public Key.Builder applySortKey(Key.Builder keyBuilder, String sortKeySuffix) {
      return keyBuilder.sortValue(prefixedSortKey(sortKeySuffix));
    }

    @Override
    public String prefixedSortKey(String sortKeySuffix) {
      return formatSortKey(typeId, sortKeySuffix);
    }

    public <S> Converted<S> converted(Converter<S, String> converter) {
      return new Converted<>(converter);
    }

    public class Converted<S> implements SortKeyBuilder<S> {
      private final Converter<S, String> converter;

      Converted(Converter<S, String> converter) {
        this.converter = converter;
      }

      @Override
      public Key.Builder applySortKey(Key.Builder keyBuilder, S sortValue) {
        return KeyBuilder.this.applySortKey(keyBuilder, converter.convert(sortValue));
      }

      @Override
      public String prefixedSortKey(S sortValue) {
        return KeyBuilder.this.prefixedSortKey(converter.convert(sortValue));
      }
    }
  }

  interface Converted<S> extends SortKeyTypePrefixBean {

    S sortValue();

    void setSortValue(S sortValue);

    Converter<S, String> sortValueConverter();

    @Override
    default void setSortKey(String sortKey) {
      setSortValue(sortValueConverter().reverse().convert(sortKey));
    }

    default String sortKeySuffix() {
      return sortValueConverter().convert(sortValue());
    }
  }
}
