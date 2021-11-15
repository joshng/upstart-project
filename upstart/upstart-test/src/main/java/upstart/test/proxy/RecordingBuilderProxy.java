package upstart.test.proxy;

import com.google.common.base.Throwables;
import com.google.common.reflect.Reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RecordingBuilderProxy<T> {
  private final List<Invocation> invocations = new ArrayList<>();
  private final T proxyInstance;
  private final Class<T> proxiedInterface;

  private RecordingBuilderProxy(Class<T> proxiedInterface) {
    proxyInstance = Reflection.newProxy(proxiedInterface, (proxy, method, args) -> {
      if (method.getDeclaringClass() != Object.class) {
        invocations.add(new Invocation(method, args));
        // note that this always returns the proxy -- which is usually appropriate for Builder-style objects, but certainly
        // not guaranteed to be correct!
        return proxy;
      } else {
        return method.invoke(this);
      }
    });
    this.proxiedInterface = proxiedInterface;
  }

  public static <T> RecordingBuilderProxy<T> newProxy(Class<T> proxiedInterface) {
    return new RecordingBuilderProxy<>(proxiedInterface);
  }

  public T proxy() {
    return proxyInstance;
  }

  public void replay(T target) {
    for (Invocation invocation : invocations) {
      invocation.apply(target);
    }
  }

  @Override
  public String toString() {
    return "RecordingBuilderProxy<" + proxiedInterface.getSimpleName() + ">@" + Integer.toHexString(hashCode());
  }

  class Invocation {
    final Method method;
    final Object[] args;

    Invocation(Method method, Object[] args) {
      this.method = method;
      this.args = args;
    }

    void apply(T builder) {
      try {
        method.invoke(builder, args);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        Throwables.throwIfUnchecked(cause);
        throw new RuntimeException(cause);
      }
    }
  }
}
