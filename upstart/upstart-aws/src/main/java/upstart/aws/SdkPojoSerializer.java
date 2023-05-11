package upstart.aws;

import software.amazon.awssdk.core.SdkPojo;
import upstart.util.collect.PersistentMap;
import upstart.util.strings.NamingStyle;

import java.util.Collection;

public class SdkPojoSerializer {
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
              ? map.plus(NamingStyle.UpperCamelCaseSplittingAcronyms.convertTo(fieldNamingStyle, field.memberName()), value)
              : map;
    }, PersistentMap::plusAll);
  }
}
