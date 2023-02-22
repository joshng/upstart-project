package upstart.aws;

import software.amazon.awssdk.core.SdkPojo;
import upstart.util.collect.PersistentMap;

import java.util.Collection;

public class SdkPojoSerializer {
  public static PersistentMap<String, Object> serialize(SdkPojo pojo) {
    return pojo.sdkFields().stream().reduce(PersistentMap.empty(), (map, field) -> {
      Object value = field.getValueOrDefault(pojo);
      if (value instanceof SdkPojo subPojo) {
        value = serialize(subPojo);
      } else if (value instanceof Collection<?> collection) {
        if (collection.isEmpty()) return map;
        value = collection.stream().map(item -> {
          if (item instanceof SdkPojo pojoitem) {
            return serialize(pojoitem);
          } else {
            return item;
          }
        }).toList();
      }
      return value != null
              ? map.plus(field.memberName(), value)
              : map;
    }, PersistentMap::plusAll);
  }
}
