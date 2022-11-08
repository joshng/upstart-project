package upstart.util.annotations;

import org.immutables.value.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An alternative to {@link Value.Immutable} that also arranges for the generated immutable type to offer a static
 * `{@code of}` factory-method rather than a {@code builder}. The generated `of` method accepts the required fields in
 * the order they appear in the type-definition.
 * @see Value.Immutable
 * @see Value.Style#allParameters
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Value.Style(allParameters = true, defaults = @Value.Immutable(builder = false))
public @interface Tuple {
}
