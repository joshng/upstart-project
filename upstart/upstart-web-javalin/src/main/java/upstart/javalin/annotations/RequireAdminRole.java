package upstart.javalin.annotations;

import upstart.javalin.AdminRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.stream.Collectors;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Http.AccessControlAnnotation
public @interface RequireAdminRole {

}
