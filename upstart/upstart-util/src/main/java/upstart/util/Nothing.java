package upstart.util;

import java.util.concurrent.CompletableFuture;

/**
 * <p>
 * A "Unit" type; like {@link Void}, but useful for representing the lack of information without resorting to 'null'
 */
public final class Nothing {
  public static final Nothing NOTHING = new Nothing();
  public static final CompletableFuture<Nothing> FUTURE = CompletableFuture.completedFuture(NOTHING);

  private Nothing() {
  }
}

