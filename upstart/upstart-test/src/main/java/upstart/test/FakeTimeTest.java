package upstart.test;

import upstart.util.concurrent.services.ScheduledService;
import upstart.util.concurrent.NamedThreadFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@UpstartTest
@ExtendWith(FakeTimeExtension.class)
public @interface FakeTimeTest {
  long UNSPECIFIED_INITIAL_MILLIS = Long.MIN_VALUE;

  /**
   * The initial time to set on the {@link Clock}, as interpreted by {@link Instant#ofEpochMilli} (default: {@link Instant#EPOCH})
   */
  long value() default UNSPECIFIED_INITIAL_MILLIS;

  /**
   * Alternative to {@link #value} for specifying the initial time as an ISO-8601 timestamp
   */
  String initialTime() default "";

  /**
   * The timezone for the {@link Clock}, as interpreted by {@link ZoneId#of(String)} (default: {@link ZoneOffset#UTC})
   */
  String timezone() default "";

  /**
   * {@link ScheduledService} type(s) to be driven based on {@link FakeTime#advance}
   */
  Class<? extends ScheduledService>[] interceptSchedules() default {};

  Class<? extends Supplier<ExecutorService>> immediateExecutorSupplier() default DefaultImmediateExecutorSupplier.class;

  class DefaultImmediateExecutorSupplier implements Supplier<ExecutorService> {
    public static final DefaultImmediateExecutorSupplier INSTANCE = new DefaultImmediateExecutorSupplier();

    @Override
    public ExecutorService get() {
      return defaultImmediateExecutorService();
    }

    public static ExecutorService defaultImmediateExecutorService() {
      return Executors.newCachedThreadPool(new NamedThreadFactory("faketime-immediate"));
    }
  }
}
