package upstart.util;

import org.immutables.value.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Value.Style(allParameters = true, defaults = @Value.Immutable(builder = false, copy = false))
@Inherited
public @interface Identifier {
}
