package upstart.util;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A fluent DSL for selecting a value based on a sequence of conditions. Selects the value
 * associated with the first matching boolean:
 * <pre>{@code
 * Optional<String> chosen = BooleanChoice
 *       .of(0 == 1, "a")
 *       .or(false, "b")
 *       .or(true, "c")
 *       .or(false, "d)
 *       .result(); // result is Optional.of("c");
 *
 * }</pre>
 * @param <T>
 */
public abstract class BooleanChoice<T> {
  private BooleanChoice() { }

  private static final BooleanChoice UNMATCHED = new BooleanChoice<Object>() {
    @Override
    public BooleanChoice<Object> or(boolean selected, Object value) { return of(selected, value); }

    @Override
    public Optional<Object> result() { return Optional.empty(); }
  };

  @SuppressWarnings("unchecked")
  public static <T> BooleanChoice<T> of(boolean selected, @Nullable T value) {
    return selected ? new Selected<>(value) : UNMATCHED;
  }

  public abstract BooleanChoice<T> or(boolean selected, T value);
  public abstract Optional<T> result();

  public T otherwise(T result) {
    return result().orElse(result);
  }

  private static class Selected<T> extends BooleanChoice<T> {
    @Nullable private final T result;

    Selected(@Nullable T result) {
      this.result = result;
    }

    @Override
    public BooleanChoice<T> or(boolean selected, T value) { return this; }

    @Override
    public Optional<T> result() { return Optional.ofNullable(result); }
  }
}
