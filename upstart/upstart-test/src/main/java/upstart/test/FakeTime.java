package upstart.test;

import com.google.common.base.Ticker;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Module;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.ProvisionListener;
import upstart.ExecutorServiceScheduler;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.services.ScheduledService;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class FakeTime {
  private final FakeTimeClock clock;
  private final Ticker ticker = new Ticker() {
    @Override
    public long read() {
      Instant instant = now;
      return TimeUnit.MILLISECONDS.toNanos(instant.toEpochMilli() + instant.getNano());
    }
  };

  private final Queue<ScheduledTask<?>> schedule = new PriorityBlockingQueue<>(11, Comparator.comparing(task -> task.nextDeadline));
  private final AtomicBoolean advancing = new AtomicBoolean(false);

  private volatile Instant now;


  public FakeTime(Instant initialTime, ZoneId timezone) {
    now = initialTime;
    clock = new FakeTimeClock(timezone);
  }

  public static FakeTime install(UpstartTestBuilder testBuilder, Instant initialTime, ZoneId timezone, ExecutorService immediateExecutor, Set<Class<? extends ScheduledService>> interceptedSchedules) {
    FakeTime fakeTime = new FakeTime(initialTime, timezone);

    testBuilder.overrideBindings(new FakeTimeModule(fakeTime, interceptedSchedules, immediateExecutor));
    return fakeTime;
  }

  public void runPendingJobs() {
    advance(Duration.ZERO);
  }

  public FakeTimeClock clock() {
    return clock;
  }

  public Instant instant() {
    return clock.instant();
  }

  public long millis() {
    return clock.millis();
  }

  public Instant advance(Duration duration) {
    checkArgument(!duration.isNegative(), "Negative duration: %s", duration);

    checkState(advancing.compareAndSet(false, true), "FakeTime.advance must only be run by a single thread");
    try {
      Instant newTime = now.plus(duration);
      boolean active;
      do {
        ScheduledTask<?> task = schedule.peek();
        active = task != null && !task.nextDeadline.isAfter(newTime);
        if (active) {
          task = schedule.poll();
          if (!task.isDone()) {
            now = task.nextDeadline;
            task.run();
            if (!task.isDone()) schedule.add(task);
          }
        }
      } while (active);
      return now = newTime;
    } finally {
      advancing.set(false);
    }
  }


  public ScheduledExecutorService scheduledExecutor(ExecutorService immediateExecutor) {
    return new FakeScheduledExecutorService(immediateExecutor);
  }

  private static Duration toDuration(long delay, TimeUnit unit) {
    return Duration.ofNanos(unit.toNanos(delay));
  }


  public class FakeScheduledExecutorService extends ForwardingExecutorService implements ScheduledExecutorService {
    private final ExecutorService immediateExecutor;

    public FakeScheduledExecutorService(ExecutorService immediateExecutor) {
      this.immediateExecutor = immediateExecutor;
    }

    @Override
    protected ExecutorService delegate() {
      return immediateExecutor;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      return addTask(null, Executors.callable(command), toDuration(delay, unit));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      return addTask(null, callable, toDuration(delay, unit));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
      return scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return addTask(toDuration(delay, unit), Executors.callable(command), toDuration(initialDelay, unit));
    }

    private <T> ScheduledFuture<T> addTask(@Nullable Duration interval, Callable<T> callable, Duration initialDelay) {
      if (isShutdown()) throw new RejectedExecutionException();
      ScheduledTask<T> task = new ScheduledTask<>(interval, callable, initialDelay);
      schedule.add(task);
      return task;
    }
  }

  private class ScheduledTask<T> extends Promise<T> implements ScheduledFuture<T> {
    private final @Nullable Duration interval;
    private final Callable<T> task;
    private Instant nextDeadline;

    public ScheduledTask(@Nullable Duration interval, Callable<T> task, Duration initialDelay) {
      checkArgument(interval == null || (!interval.isNegative() && !interval.isZero()), "Illegal scheduling interval: %s", interval);
      this.interval = interval;
      this.task = task;
      nextDeadline = clock.instant().plus(initialDelay);
    }

    public void run() {
      if (interval != null) {
        try {
          task.call();
          nextDeadline = clock.instant().plus(interval);
        } catch (Throwable e) {
          completeExceptionally(e);
        }
      } else {
        tryComplete(task);
      }
    }

    public long getDelay(TimeUnit unit) {
      return unit.convert(Duration.between(clock.instant(), nextDeadline));
    }

    @Override
    public int compareTo(Delayed o) {
      return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
    }
  }

  private class FakeTimeClock extends Clock {
    private final ZoneId zoneId;

    private FakeTimeClock(ZoneId zoneId) {
      this.zoneId = zoneId;
    }

    @Override
    public ZoneId getZone() {
      return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return zoneId.equals(zone) ? this : new FakeTimeClock(zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static class FakeTimeModule implements Module {
    private final FakeTime fakeTime;
    private final Set<Class<? extends ScheduledService>> interceptedSchedules;
    private final ExecutorService immediateExecutor;

    public FakeTimeModule(FakeTime fakeTime, Set<Class<? extends ScheduledService>> interceptedSchedules, ExecutorService immediateExecutor) {
      this.fakeTime = fakeTime;
      this.interceptedSchedules = interceptedSchedules;
      this.immediateExecutor = immediateExecutor;
    }

    @Override
    public void configure(Binder binder) {
      binder.bind(Clock.class).toInstance(fakeTime.clock);
      binder.bind(Ticker.class).toInstance(fakeTime.ticker);
      binder.bind(FakeTime.class).toInstance(fakeTime);
      ExecutorServiceScheduler.Module.bindExecutorService(binder).toInstance(fakeTime.scheduledExecutor(immediateExecutor));
      if (!interceptedSchedules.isEmpty()) {
        binder.bindListener(new AbstractMatcher<>() {
          @Override
          public boolean matches(Binding<?> binding) {
            return interceptedSchedules.contains(binding.getKey().getTypeLiteral().getRawType());
          }
        }, new ProvisionListener() {
          @Override
          public <T> void onProvision(ProvisionInvocation<T> provision) {
            ScheduledService service = (ScheduledService) provision.provision();
            service.decorateExecutor(fakeTime::scheduledExecutor);
          }
        });
      }
    }
  }
}
