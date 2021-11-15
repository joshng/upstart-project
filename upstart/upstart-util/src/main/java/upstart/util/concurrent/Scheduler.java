package upstart.util.concurrent;

import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface Scheduler extends Executor {
  ListeningScheduledExecutorService scheduledExecutorService();

  Clock clock();

  default ListenableScheduledFuture<?> scheduleAtFixedRate(Duration initialDelay, Duration period, Runnable command) {
    return scheduledExecutorService().scheduleAtFixedRate(command, initialDelay.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS);
  }

  default ListenableScheduledFuture<?> schedule(Duration delay, Runnable command) {
    return scheduledExecutorService().schedule(command, delay.toNanos(), TimeUnit.NANOSECONDS);
  }


  default Instant now() {
    return clock().instant();
  }
}
