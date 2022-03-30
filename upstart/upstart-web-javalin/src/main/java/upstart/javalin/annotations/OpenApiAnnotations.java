package upstart.javalin.annotations;

import com.google.common.collect.ObjectArrays;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Include({OpenApi.class, OpenApiResponse.class, OpenApiContent.class})
interface OpenApiAnnotations {
  String DEFAULT_STATUS = "200";

  static OpenApi openApi(Optional<OpenApi> providedAnnotation, OpenApiResponse response) {
    ImmutableOpenApi.Builder builder = ImmutableOpenApi.builder();
    providedAnnotation.ifPresentOrElse(
            provided -> builder.from(provided)
                    .responses(ObjectArrays.concat(response, provided.responses())),
            () -> builder.responses(response));
    return builder.build();
  }

  static ImmutableOpenApiResponse.Builder responseBuilder() {
    return ImmutableOpenApiResponse.builder();
  }

  static ImmutableOpenApiContent.Builder contentBuilder() {
    return ImmutableOpenApiContent.builder();
  }
}
