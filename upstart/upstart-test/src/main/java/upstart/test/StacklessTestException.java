package upstart.test;

public class StacklessTestException extends RuntimeException {
  public StacklessTestException() {
    this("Exception for TEST");
  }

  public StacklessTestException(String message) {
    super(message);
  }

  public StacklessTestException(String message, Throwable cause) {
    super(message, cause);
  }

  public StacklessTestException(Throwable cause) {
    super(cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
