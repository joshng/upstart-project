package upstart.aws;

import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import upstart.util.collect.PersistentMap;
import upstart.util.strings.NamingStyle;

import java.util.Collection;
import java.util.regex.Pattern;

public class SdkPojoSerializer {
  private static final Pattern ALL_UPPERCASE = Pattern.compile("^[A-Z]+$");
  public static PersistentMap<String, Object> serialize(SdkPojo pojo, NamingStyle fieldNamingStyle) {
    return pojo.sdkFields().stream().reduce(PersistentMap.empty(), (map, field) -> {
      Object value = field.getValueOrDefault(pojo);
      if (value instanceof SdkPojo subPojo) {
        value = serialize(subPojo, fieldNamingStyle);
      } else if (value instanceof Collection<?> collection) {
        if (collection.isEmpty()) return map;
        value = collection.stream().map(item -> {
          if (item instanceof SdkPojo pojoitem) {
            return serialize(pojoitem, fieldNamingStyle);
          } else {
            return item;
          }
        }).toList();
      }
      return value != null
              ? map.plus(convertFieldName(field, fieldNamingStyle), value)
              : map;
    }, PersistentMap::plusAll);
  }

  private static String convertFieldName(SdkField<?> field, NamingStyle fieldNamingStyle) {
    String name = field.memberName();
    NamingStyle sdkNamingStyle = ALL_UPPERCASE.matcher(name).matches()
            ? NamingStyle.UpperCamelCaseGroupingAcronyms
            : NamingStyle.UpperCamelCaseSplittingAcronyms;
    return sdkNamingStyle.convertTo(fieldNamingStyle, name);
  }
}
