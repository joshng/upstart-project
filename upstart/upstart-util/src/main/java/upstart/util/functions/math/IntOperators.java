package upstart.util.functions.math;

import java.util.function.IntBinaryOperator;

public enum IntOperators implements IntBinaryOperator {
  add(Integer::sum),
  subtract((input1, input2) -> input1 - input2),
  multiply((input1, input2) -> input1 * input2),
  divide((input1, input2) -> input1 / input2);

  public final IntBinaryOperator operator;

  IntOperators(IntBinaryOperator operator) {
    this.operator = operator;
  }

  @Override
  public int applyAsInt(int left, int right) {
    return operator.applyAsInt(left, right);
  }
}
