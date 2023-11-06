package upstart.javalin.context;

import io.javalin.http.Context;
import upstart.util.context.AsyncLocal;
import upstart.util.context.TransientContext;

import java.util.Optional;

public class AsyncJavalinContext {
  private static final AsyncLocal<Context> ASYNC_JAVALIN_CONTEXT = AsyncLocal.newImmutableAsyncLocal("JAVALIN_CONTEXT");
  private AsyncJavalinContext() {}

  public static Optional<Context> currentRequestContext() {
    return ASYNC_JAVALIN_CONTEXT.getOptional();
  }

  public static <T> Optional<T> currentRequestAttribute(RequestAttribute<T> attribute) {
    return currentRequestContext().flatMap(attribute::getOptional);
  }

  public static TransientContext transientContext(Context context) {
    return ASYNC_JAVALIN_CONTEXT.contextWithValue(context);
  }
}
