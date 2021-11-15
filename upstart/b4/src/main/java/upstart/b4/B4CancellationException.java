package upstart.b4;

import upstart.util.exceptions.Exceptions;

import java.util.concurrent.CancellationException;

public class B4CancellationException extends CancellationException {
  B4CancellationException() {
    super("b4 execution was aborted");
  }

  public static boolean isCancellation(Throwable e) {
    return Exceptions.isCausedBy(CancellationException.class, e);
  }
}
