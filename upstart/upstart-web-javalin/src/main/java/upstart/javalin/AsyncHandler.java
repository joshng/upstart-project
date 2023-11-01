package upstart.javalin;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import upstart.javalin.context.AsyncJavalinContext;
import upstart.util.concurrent.Promise;

public interface AsyncHandler extends Handler {
  Promise<?> handleAsync(Context ctx);

  @Override
  default void handle(Context ctx) throws Exception {
    AsyncJavalinContext.transientContext(ctx).runInContext(() -> ctx.future(handleAsync(ctx)));
  }
}
