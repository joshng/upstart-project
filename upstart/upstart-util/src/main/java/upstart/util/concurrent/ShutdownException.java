package upstart.util.concurrent;

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.concurrent.CancellationException;

public class ShutdownException extends CancellationException {
  public ShutdownException() {
  }

  public ShutdownException(String message) {
    super(message);
  }

  public static void throwIf(boolean condition) {
    if (condition) {
      throw new ShutdownException();
    }
  }

  public static void throwIf(boolean condition, String message) {
    if (condition) throw new ShutdownException(message);
  }

  public static void throwIf(boolean condition, String template, Object... args) {
    if (condition) throw new ShutdownException(Strings.lenientFormat(template, args));
  }
}
