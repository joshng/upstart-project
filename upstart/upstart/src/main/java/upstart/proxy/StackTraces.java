package upstart.proxy;

import com.google.common.collect.ObjectArrays;
import com.google.inject.internal.util.StackTraceElements;
//import javassist.ClassPool;
//import javassist.CtClass;
//import javassist.CtMethod;
import upstart.util.collect.Optionals;

import java.lang.reflect.Method;
import java.util.Optional;

public class StackTraces {
  public static boolean prependStackTrace(Method method, Throwable throwable) {
    return getStackTraceElement(method)
            .map(stackTraceElement -> {
              throwable.setStackTrace(ObjectArrays.concat(stackTraceElement, throwable.getStackTrace()));
              return true;
            }).orElse(false);
  }

  public static <E extends Throwable> E throwWithPrependedStackTrace(Method method, E throwable) throws E {
    prependStackTrace(method, throwable);
    throw throwable;
  }

  public static Optional<StackTraceElement> getStackTraceElement(Method m) {
    return Optionals.asInstance(StackTraceElements.forMember(m), StackTraceElement.class);
    /* *********************************
    // NOTE: the below code using javassist seems to work, but might as well use the solution (un)supported by guice
    return Optionals.exceptionAsOptional(() -> {
      ClassPool pool = ClassPool.getDefault();
      String methodName = m.getName();
      String declaringClassName = m.getDeclaringClass().getCanonicalName();
      CtClass cc = pool.get(declaringClassName);
      CtMethod javassistMethod = cc.getDeclaredMethod(methodName);
      int linenumber = javassistMethod.getMethodInfo().getLineNumber(0);

      // this is a guess at the filename; probably not always correct... can we do better?
      String fileName = declaringClassName + ".java";
      return new StackTraceElement(declaringClassName, methodName, fileName, linenumber);
    });
    ********************************* */
  }
}
