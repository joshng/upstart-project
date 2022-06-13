package upstart.util.functions;

@FunctionalInterface
public interface AsyncConsumer<T> extends AsyncFunction<T, Void> {
  static <T> AsyncConsumer<T> asyncConsumer(AsyncConsumer<T> consumer) {
    return consumer;
  }
}
