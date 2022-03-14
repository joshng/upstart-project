package upstart.util.concurrent;

import com.google.common.base.Joiner;
import upstart.util.exceptions.UncheckedInterruptedException;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Duration;

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
}
