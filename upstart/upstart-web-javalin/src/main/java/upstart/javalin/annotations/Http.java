package upstart.javalin.annotations;

import io.javalin.http.HandlerType;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Http {
  Verb verb();
  /** The route-pattern to associate with the annotated method, as understood by {@link io.javalin.Javalin} */
  String path();

  OpenApiResponse responseDoc() default @OpenApiResponse(status = OpenApiAnnotations.DEFAULT_STATUS);

  enum Verb {
    GET(HandlerType.GET),
    POST(HandlerType.POST),
    PUT(HandlerType.PUT),
    HEAD(HandlerType.HEAD),
    DELETE(HandlerType.DELETE),
    OPTIONS(HandlerType.OPTIONS),
    PATCH(HandlerType.PATCH)
    ;

    public final HandlerType handlerType;

    Verb(HandlerType handlerType) {

      this.handlerType = handlerType;
    }
  }

  /**
   * Marker-annotation to indicate that the annotated annotation-type determines access-control rights required by
   * the methods it annotates
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.ANNOTATION_TYPE)
  @interface AccessControlAnnotation {
  }
}
