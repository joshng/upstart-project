package upstart.util.functions.math;

import java.util.function.LongBinaryOperator;

public enum LongOperators implements LongBinaryOperator {
  add(Long::sum),
  subtract((input1, input2) -> input1 - input2),
  multiply((input1, input2) -> input1 * input2),
  divide((input1, input2) -> input1 / input2);

  public final LongBinaryOperator operator;

  LongOperators(LongBinaryOperator operator) {
    this.operator = operator;
  }

  @Override
  public long applyAsLong(long left, long right) {
    return operator.applyAsLong(left, right);
  }
}
