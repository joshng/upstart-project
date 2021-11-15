package upstart.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
public abstract class SetTransition<T> {
  public static <T> ImmutableSetTransition.Builder<T> builder() {
    return ImmutableSetTransition.builder();
  }

  public static <T> SetTransition<T> ofThese(Set<? extends T> oldSet, Set<? extends T> newSet) {
    return ImmutableSetTransition.of(oldSet, newSet);
  }

  @Value.Parameter
  public abstract Set<T> oldSet();

  @Value.Parameter
  public abstract Set<T> newSet();

  @Value.Derived
  public Set<T> removals() {
    return ImmutableSet.copyOf(Sets.difference(oldSet(), newSet()));
  }

  @Value.Derived
  public Set<T> additions() {
    return ImmutableSet.copyOf(Sets.difference(newSet(), oldSet()));
  }
}
