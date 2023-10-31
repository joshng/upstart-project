package upstart.javalin.context;

import io.javalin.http.Context;
import upstart.javalin.UpstartJavalinModule;
import upstart.util.context.AsyncContext;
import upstart.util.context.AsyncLocal;
import upstart.util.context.TransientContext;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

public class AsyncJavalinContext {
  private static final String FINAL_ASYNC_CONTEXT = "__finalAsyncContext__";
  private static final AsyncLocal<Context> ASYNC_JAVALIN_CONTEXT = AsyncLocal.newImmutableAsyncLocal("JAVALIN_CONTEXT");
  private static final RequestAttribute<TransientContext> FINAL_ASYNC_CONTEXT_ATTRIBUTE = new RequestAttribute<>(FINAL_ASYNC_CONTEXT);
  private AsyncJavalinContext() {}

  public static Optional<Context> currentRequestContext() {
    return ASYNC_JAVALIN_CONTEXT.getOptional();
  }

  public static TransientContext finalAsyncContext(Context ctx) {
    return FINAL_ASYNC_CONTEXT_ATTRIBUTE.getOptional(ctx).orElse(AsyncContext.emptyContext());
  }

  private static void initialize(Context ctx) {
    checkState(ASYNC_JAVALIN_CONTEXT.compareAndSet(null, ctx), "Context already initialized");
  }

  private static void storeFinalAsyncContext(Context ctx) {
    assert ASYNC_JAVALIN_CONTEXT.get() == ctx;
    FINAL_ASYNC_CONTEXT_ATTRIBUTE.set(ctx, AsyncContext.snapshot());
    AsyncContext.clear();
  }

  public static class Module extends UpstartJavalinModule {
    @Override
    protected void configure() {
      addJavalinWebBinding().toInstance(javalin -> {
        javalin.registerPlugin(app -> {
          app.before(AsyncJavalinContext::initialize);
          app.after(AsyncJavalinContext::storeFinalAsyncContext);
        });
      });
    }
  }
}
