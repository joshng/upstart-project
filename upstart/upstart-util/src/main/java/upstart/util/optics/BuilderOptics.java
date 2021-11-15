package upstart.util.optics;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * An intermediary to assist with {@link Lens} constructions for immutable types that are constructed with the
 * "Builder" pattern (ie, using a separate type {@link B} to collect initialization parameters to be applied to a new
 * instance via {@link #build}).
 */
public interface BuilderOptics<T, B> {
  B toBuilder(T instance);
  T build(B builder);

  static <T, B> BuilderOptics<T, B> of(Function<T, B> toBuilder, Function<B, T> build) {
    return new BuilderOptics<>() {
      @Override
      public B toBuilder(T instance) {
        return toBuilder.apply(instance);
      }

      @Override
      public T build(B builder) {
        return build.apply(builder);
      }
    };
  }

  default T update(T instance, UnaryOperator<B> builder) {
    return build(builder.apply(toBuilder(instance)));
  }

  default <V> Setter<T, V> setter(BuilderSetter<B, V> builderSetter) {
    return (instance, value) -> build(builderSetter.set(toBuilder(instance), value));
  }

  default <V> Lens<T, V> lens(Getter<T, V> getter, BuilderSetter<B, V> builderSetter) {
    return Lens.of(getter, setter(builderSetter));
  }

  interface BuilderSetter<B, V> {
    B set(B builder, V value);
  }
}
