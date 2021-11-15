package upstart.b4;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Singleton
public class B4StateStore {
  private final Map<Object, Object> stateStore = new ConcurrentHashMap<>();

  public <K, V> V updateState(K key, Class<V> stateClass, Function<Optional<V>, V> updater) {
    return stateClass.cast(stateStore.compute(key, (k, v) -> updater.apply(Optional.ofNullable(v).map(stateClass::cast))));
  }

  public <K, V> Optional<V> getState(K key, Class<V> stateClass) {
    return Optional.ofNullable(stateStore.get(key)).map(stateClass::cast);
  }
}
