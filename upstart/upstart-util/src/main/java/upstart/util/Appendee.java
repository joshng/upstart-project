package upstart.util;

import java.io.IOException;
import java.util.stream.Collector;

import static upstart.util.exceptions.UncheckedIO.*;

public interface Appendee {
  void appendTo(Appendable appendable) throws IOException;

  static void appendUnchecked(Appendable appendable, Appendee appendee) {
    runUnchecked(() -> appendee.appendTo(appendable));
  }

  default StringBuilder appendTo(StringBuilder builder) {
    appendUnchecked(builder, this);
    return builder;
  }

  static Collector<Appendee, ?, StringBuilder> appendingToStringBuilder() {
    return appendingTo(new StringBuilder());
  }

  static Collector<Appendee, ?, StringBuilder> joiningToStringBuilder(String separator) {
    return joiningTo(separator, new StringBuilder());
  }

  static <A extends Appendable> Collector<Appendee, ?, A> appendingTo(A appendable) {
    return Collector.of(() -> appendable, Appendee::appendUnchecked, Appendee::throwParallelUnsupported);
  }

  static <A extends Appendable> Collector<Appendee, ?, A> joiningTo(String separator, A builder) {
    boolean[] first = {true};
    return Collector.of(() -> builder, (b, item) -> runUnchecked(() -> {
      if (first[0]) {
        first[0] = false;
      } else {
        b.append(separator);
      }
      item.appendTo(b);
    }), Appendee::throwParallelUnsupported);
  }

  private static <T> T throwParallelUnsupported(Object a, Object b) {
    throw new UnsupportedOperationException("parallel streams unsupported");
  }
}
