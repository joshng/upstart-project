package upstart.util.concurrent;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import upstart.util.exceptions.UncheckedInterruptedException;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Duration;
import java.util.List;

public class Threads {
  public static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      throw UncheckedInterruptedException.propagate(e);
    }
  }

  public static ThreadInfo[] getThreadDump() {
    return ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
  }

  public static String formatThreadDump() {
    return Joiner.on("\n").join(getThreadDump());
  }

  /**
   * Returns the stacktrace of the caller.<p/>
   *
   * NOTE: While attempting to be as efficient as possible, this method is probably still rather costly, so it is
   * probably inappropriate to use from high-traffic call-sites.
   * @see Throwables#lazyStackTrace
   */
  public static List<StackTraceElement> currentStackTrace() {
    List<StackTraceElement> fromHere = Throwables.lazyStackTrace(new RuntimeException());
    return fromHere.subList(1, fromHere.size());
  }
}
