package upstart.util.reflect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class ReflectTest {

  @Test
  void allDeclaredMethods() {
    List<Method> methods = Reflect.allDeclaredMethods(
            SubClass.class,
            Reflect.LineageOrder.SubclassBeforeSuperclass
    )
            .filter(Modifiers.Concrete.member())
            .toList();

    assertThat(methods).hasSize(1);
  }

  abstract static class BaseClass<T> {
    abstract int producesSyntheticMethod(T arg);
  }

  static class SubClass extends BaseClass<String> {

    @Override
    int producesSyntheticMethod(String arg) {
      return arg.length();
    }
  }
}