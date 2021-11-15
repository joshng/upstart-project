package upstart.util;

import com.google.common.collect.ImmutableList;
import upstart.util.exceptions.Exceptions;
import upstart.util.exceptions.MultiException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class MultiMethodInvoker<T> {
  private final ImmutableList<Method> methods;
  private static final MultiMethodInvoker NULL_INVOKER = new MultiMethodInvoker<Object>(ImmutableList.of()) {
    @Override public void invoke(Object target, boolean continueAfterException, Object... args) {
      // noooop!
    }
  };

  public static <T> MultiMethodInvoker<T> forMethodsMatching(Class<? extends T> targetClass,
                                                             Predicate<? super Method> methodSelector
  ) {
    Stream<Method> declaredMethods = Reflect.allDeclaredMethods(targetClass, Reflect.LineageOrder.SuperclassBeforeSubclass);
    ImmutableList<Method> methods = declaredMethods
            .filter(methodSelector)
            .collect(ImmutableList.toImmutableList());

    @SuppressWarnings("unchecked") MultiMethodInvoker<T> result = methods.isEmpty() ? NULL_INVOKER : new MultiMethodInvoker<>(methods);
    return result;
  }

  public static <T> MultiMethodInvoker<T> forAnnotatedMethods(Class<? extends T> targetClass, Class<? extends Annotation> annotationClass) {
    return forMethodsMatching(targetClass, Reflect.annotationPredicate(annotationClass));
  }

  public static <T> Dispatcher<T> machingMethodDispatcher(Predicate<? super Method> methodPredicate) {
    return new Dispatcher<>(methodPredicate);
  }

  public MultiMethodInvoker(ImmutableList<Method> methods) {
    this.methods = methods;
    AccessibleObject.setAccessible(methods.toArray(new AccessibleObject[0]), true);
  }

  @SuppressWarnings("deprecation")
  public void invoke(T target, boolean continueAfterException, Object... args) {
    MultiException multiException = MultiException.Empty;
    for (Method method : methods) {
      try {
        method.invoke(target, args);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        if (!continueAfterException) throw Exceptions.throwUnchecked(e.getCause());
        multiException = multiException.with(e.getCause());
      } catch (Exception e) {
        if (!continueAfterException) throw Exceptions.throwUnchecked(e);
        multiException = multiException.with(e);
      }
    }
    multiException.throwRuntimeIfAny();
  }

  public boolean isEmpty() {
    return methods.isEmpty();
  }

  public static class Dispatcher<T> {
    private final ClassValue<MultiMethodInvoker<Object>> classInvokers;

    public Dispatcher(Predicate<? super Method> methodPredicate) {
      classInvokers = new ClassValue<MultiMethodInvoker<Object>>() {
        @Override
        protected MultiMethodInvoker<Object> computeValue(Class<?> type) {
          return forMethodsMatching(type, methodPredicate);
        }
      };
    }

    @SuppressWarnings("unchecked")
    public void dispatch(T target, boolean continueAfterException, Object... args) {
      classInvokers.get(target.getClass()).invoke(target, continueAfterException, args);
    }
  }
}
