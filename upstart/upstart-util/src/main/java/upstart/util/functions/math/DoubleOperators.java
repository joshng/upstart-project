package upstart.util.functions.math;

import java.util.function.DoubleBinaryOperator;

public enum DoubleOperators implements DoubleBinaryOperator {
  add(Double::sum),
  subtract((input1, input2) -> input1 - input2),
  multiply((input1, input2) -> input1 * input2),
  divide((input1, input2) -> input1 / input2);

  public final DoubleBinaryOperator operator;

  DoubleOperators(DoubleBinaryOperator operator) {
    this.operator = operator;
  }

  @Override
  public double applyAsDouble(double left, double right) {
    return operator.applyAsDouble(left, right);
  }
}
