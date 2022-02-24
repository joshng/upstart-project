package upstart.javalin.annotations;

import upstart.web.WebServerConfig;

import javax.inject.Inject;
import java.util.function.Consumer;

public class HttpRoutes<T> {
  private final AnnotatedEndpointHandler<T> handler;
  private final HttpUrl contextPath;

  @Inject
  HttpRoutes(AnnotatedEndpointHandler<T> handler, WebServerConfig webServerConfig) {
    this(HttpUrl.of(webServerConfig.contextPath()), handler);
  }

  HttpRoutes(HttpUrl contextPath, AnnotatedEndpointHandler<T> handler) {
    this.handler = handler;
    this.contextPath = contextPath;
  }

  public HttpUrl urlFor(Consumer<? super T> methodInvoker) {
    return contextPath.resolve(handler.urlFor(methodInvoker));
  }
}
