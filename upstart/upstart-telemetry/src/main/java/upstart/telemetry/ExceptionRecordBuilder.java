package upstart.telemetry;

import com.google.common.base.MoreObjects;
import io.upstartproject.avro.event.ExceptionEvent;
import io.upstartproject.avro.event.ExceptionRecord;
import io.upstartproject.avro.event.StackElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExceptionRecordBuilder {
  public ExceptionEvent toExceptionEvent(Throwable throwable) {
    return new ExceptionEvent(toExceptionRecord(throwable));
  }

  public ExceptionRecord toExceptionRecord(Throwable throwable) {
    List<StackElement> stack = Stream.of(throwable.getStackTrace())
            .map(ExceptionRecordBuilder::toStackElement)
            .collect(Collectors.toList());
    ExceptionRecord cause = Optional.ofNullable(throwable.getCause())
            .map(this::toExceptionRecord)
            .orElse(null);
    List<ExceptionRecord> suppressedExceptions = new ArrayList<>();
    for (Throwable suppressed : throwable.getSuppressed()) {
      suppressedExceptions.add(toExceptionRecord(suppressed));
    }
    return new ExceptionRecord(
            throwable.getClass().getName(),
            MoreObjects.firstNonNull(throwable.getMessage(), "[null]"),
            stack,
            cause,
            suppressedExceptions
    );
  }

  private static StackElement toStackElement(StackTraceElement ste) {
    return new StackElement(ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber());
  }
}
