package upstart.util.functions.math;

import java.util.function.BinaryOperator;

public record Arithmetic(
    BinaryOperator<Number> add,
    BinaryOperator<Number> subtract,
    BinaryOperator<Number> multiply,
    BinaryOperator<Number> divide
) {

  public BinaryOperator<Number> operator(Operator operator) {
    return switch (operator) {
      case add -> add;
      case subtract -> subtract;
      case multiply -> multiply;
      case divide -> divide;
    };
  }

  public enum Operator {
    add, subtract, multiply, divide;

    public BinaryOperator<Number> of(Arithmetic arithmetic) {
      return arithmetic.operator(this);
    }
  }
}
