package upstart.b4;

import java.util.Optional;

public class B4TaskFailedException extends RuntimeException {
  public B4TaskFailedException(TargetInstanceId failedTaskId, Throwable cause) {
    super("Task failed: " + failedTaskId + Optional.ofNullable(cause).map(c -> " (" + c.getMessage() + ")").orElse(""), cause);
  }
}
