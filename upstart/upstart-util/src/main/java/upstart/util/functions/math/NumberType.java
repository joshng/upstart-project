package upstart.util.functions.math;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public enum NumberType {
  Integer(new Arithmetic(
          (input1, input2) -> input1.intValue() + input2.intValue(),
          (input1, input2) -> input1.intValue() - input2.intValue(),
          (input1, input2) -> input1.intValue() * input2.intValue(),
          (input1, input2) -> input1.intValue() / input2.intValue()
  )),
  Long(new Arithmetic(
          (input1, input2) -> input1.longValue() + input2.longValue(),
          (input1, input2) -> input1.longValue() - input2.longValue(),
          (input1, input2) -> input1.longValue() * input2.longValue(),
          (input1, input2) -> input1.longValue() / input2.longValue()
  )),
  Float(new Arithmetic(
          (input1, input2) -> input1.floatValue() + input2.floatValue(),
          (input1, input2) -> input1.floatValue() - input2.floatValue(),
          (input1, input2) -> input1.floatValue() * input2.floatValue(),
          (input1, input2) -> input1.floatValue() / input2.floatValue()
  )),
  Double(new Arithmetic(
          (input1, input2) -> input1.doubleValue() + input2.doubleValue(),
          (input1, input2) -> input1.doubleValue() - input2.doubleValue(),
          (input1, input2) -> input1.doubleValue() * input2.doubleValue(),
          (input1, input2) -> input1.doubleValue() / input2.doubleValue()
  ));

  public final Arithmetic arithmetic;

  NumberType(Arithmetic arithmetic) {
    this.arithmetic = arithmetic;
  }

  public Arithmetic arithmetic() {
    return arithmetic;
  }

  public UnaryOperator<Number> converter() {
    return switch (this) {
      case Integer -> Number::intValue;
      case Long -> Number::longValue;
      case Float -> Number::floatValue;
      case Double -> Number::doubleValue;
    };
  }

  public Number parse(String str) {
    return parser().apply(str);
  }

  public Function<String, ? extends Number> parser() {
    return switch (this) {
      case Integer -> java.lang.Integer::valueOf;
      case Long -> java.lang.Long::valueOf;
      case Float -> java.lang.Float::valueOf;
      case Double -> java.lang.Double::valueOf;
    };
  }
}
