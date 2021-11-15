package upstart.util.exceptions;

public class UncheckedInterruptedException extends RuntimeException {
  private UncheckedInterruptedException(InterruptedException cause) {
    super(cause);
  }

  public static UncheckedInterruptedException propagate(InterruptedException cause) {
    Thread.currentThread().interrupt();
    throw new UncheckedInterruptedException(cause);
  }

  public static void propagate(Fallible<InterruptedException> block) {
    try {
      block.runOrThrow();
    } catch (InterruptedException e) {
      throw propagate(e);
    }
  }

  public static <T> T propagate(FallibleSupplier<T, InterruptedException> block) {
    try {
      return block.getOrThrow();
    } catch (InterruptedException e) {
      throw propagate(e);
    }
  }
}
