package upstart.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A meta-annotation for test annotations that should be processed by {@link UpstartExtension}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Repeatable(UpstartTestAnnotations.class)
@UpstartTest
public @interface UpstartTestAnnotation {
  Class<? extends UpstartTestInitializer> value();

}
