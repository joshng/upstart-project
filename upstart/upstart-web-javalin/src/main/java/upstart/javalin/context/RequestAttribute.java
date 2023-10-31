package upstart.javalin.context;

import io.javalin.http.Context;
import upstart.util.concurrent.MutableReference;

import java.util.Optional;

public class RequestAttribute<T> {
  private final String key;

  public RequestAttribute(String key) {
    this.key = key;
  }

  public T get(Context ctx) {
    return ctx.attribute(key);
  }

  public void set(Context ctx, T value) {
    ctx.attribute(key, value);
  }

  public Optional<T> getOptional(Context ctx) {
    return Optional.ofNullable(get(ctx));
  }

  public MutableReference<T> of(Context ctx) {
    return new MutableReference<>() {
      @Override
      public void set(T value) {
        RequestAttribute.this.set(ctx, value);
      }

      @Override
      public T get() {
        return RequestAttribute.this.get(ctx);
      }
    };
  }
}
