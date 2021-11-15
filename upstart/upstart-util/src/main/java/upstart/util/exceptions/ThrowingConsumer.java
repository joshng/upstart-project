package upstart.util.exceptions;

@FunctionalInterface
public interface ThrowingConsumer<T> extends FallibleConsumer<T, Exception> {
}
