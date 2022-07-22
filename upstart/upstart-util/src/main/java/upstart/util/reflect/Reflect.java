package upstart.util.reflect;

import upstart.util.collect.MoreStreams;
import upstart.util.collect.Optionals;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class Reflect {
  @SuppressWarnings("unchecked")
  public static <T> T blindCast(Object obj) {
    return (T) obj;
  }

  public static Type[] getGenericTypeParameters(Type klass) {
    Type superclass = klass;
    while (superclass instanceof Class) {
      superclass = ((Class) superclass).getGenericSuperclass();
      if (superclass == Object.class) throw new IllegalStateException("Missing type parameter for " + klass);
    }
    if (superclass == null) {
      throw new AssertionError("Invalid class: " + klass);
    }
    ParameterizedType parameterized = (ParameterizedType) superclass;
    return parameterized.getActualTypeArguments();
  }

  @SuppressWarnings({"unchecked"})
  public static <T extends Type> T getGenericTypeParameter(Type type, int parameterIndex) {
    return (T) getGenericTypeParameters(type)[parameterIndex];
  }

  @SuppressWarnings({"unchecked"})
  public static <T extends Type> T getFirstGenericType(Type type) {
    return (T) getGenericTypeParameter(type, 0);
  }

  public static Stream<Class<?>> superclassLineage(Class<?> start) {
    Stream<Class<?>> heritage = MoreStreams.generate(start, Class::getSuperclass);
    return heritage.takeWhile(cls -> cls != Object.class);
  }

  @SuppressWarnings("unchecked")
  public static <T> Class<T> getUnenhancedClass(Class<? extends T> possiblyEnhancedClass) {
    Class<?> c = possiblyEnhancedClass;
    while (c.getName().contains("$$")) {
      c = c.getSuperclass();
    }
    return (Class<T>) c;
  }

  public static <T> Stream<Method> allDeclaredMethods(Class<? extends T> targetClass, LineageOrder order) {
    Stream<? extends Class<?>> lineage = superclassLineage(targetClass);
    if (order != LineageOrder.SubclassBeforeSuperclass) lineage = lineage.collect(toImmutableList()).reverse().stream();

    return lineage.flatMap(cls -> Stream.of(cls.getDeclaredMethods())
            .filter(m -> !m.isSynthetic()));
  }

  public static Stream<Method> allAnnotatedMethods(Class<?> annotatedClass, Class<? extends Annotation> annotationClass, LineageOrder lineageOrder) {
    return allDeclaredMethods(annotatedClass, lineageOrder).filter(annotationPredicate(annotationClass));
  }

  public static Optional<SerializedLambda> serializedLambda(Serializable maybeLambda) {
    Class<? extends Serializable> cls = maybeLambda.getClass();
    if (!isPossiblyLambda(cls)) {
      return Optional.empty();
    }
    try {
      return Optionals.asInstance(
              setAccessible(cls.getDeclaredMethod("writeReplace")).invoke(maybeLambda),
              SerializedLambda.class
      );
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static boolean isPossiblyLambda(Class<? extends Serializable> cls) {
    return cls.isSynthetic() && cls.getSimpleName().contains("$lambda");
  }

  public static <T extends AccessibleObject> T setAccessible(T accessible) {
    if (!accessible.isAccessible()) accessible.setAccessible(true);
    return accessible;
  }

  public static String describeMethodWithAnnotations(Method method) {
    StringBuilder builder = new StringBuilder();
    for (Annotation annotation : method.getAnnotations()) {
      builder.append('@').append(annotation.annotationType().getSimpleName()).append(' ');
    }
    return builder.append(method.toGenericString()).toString();
  }

  public enum LineageOrder {
    SubclassBeforeSuperclass,
    SuperclassBeforeSubclass
  }

  public static Stream<Class<?>> containingClasses(Class<?> start) {
    return MoreStreams.generate(start.getEnclosingClass(), Class::getEnclosingClass);
  }

  public static <A extends Annotation> Predicate<AnnotatedElement> annotationPredicate(Class<A> annotationClass) {
    return e -> e.isAnnotationPresent(annotationClass);
  }

  @SuppressWarnings("unchecked")
  public static <T, R extends T> Class<R> classForName(String name, Class<T> baseType) {
    try {
      return (Class<R>) Class.forName(name).asSubclass(baseType);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T newInstance(String className, Class<T> baseType) {
    return newInstance(classForName(className, baseType));
  }

  public static <T> T newInstance(Class<? extends T> klass) {
    try {
      return klass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<Class<?>> allSupertypesAndInterfaces(Class<?> type) {
    Set<Class<?>> visited = new HashSet<>();
    return superclassLineage(type).flatMap(t -> collectInterfaces(t, visited));
  }

  public static Stream<Class<?>> allInterfaces(Class<?> type) {
    return collectInterfaces(type, new HashSet<>());
  }

  public static <A extends Annotation> Stream<A> findSuperAnnotations(Class<A> annotationType, Class<?> target) {
    return allSupertypesAndInterfaces(target)
            .flatMap(type -> Stream.of(type.getAnnotationsByType(annotationType)));
  }

  public static <A extends Annotation> Stream<A> findMetaAnnotations(Class<A> annotationType, Class<?> target) {
    return MoreStreams.filter(allReachableMetaAnnotations(target), annotationType);
  }

  public static Stream<Annotation> allReachableMetaAnnotations(Class<?> target) {
    Set<Annotation> visited = new HashSet<>();
    return allSupertypesAndInterfaces(target)
            .flatMap(type -> allMetaAnnotations(type, visited));
  }

  public static <A extends Annotation> Stream<A> findMetaAnnotations(Class<A> annotationType, AnnotatedElement target) {
    return MoreStreams.filter(allMetaAnnotations(target), annotationType);
  }

  public static Stream<Annotation> allMetaAnnotations(AnnotatedElement target) {
    return allMetaAnnotations(target, new HashSet<>());
  }

  private static Stream<Annotation> allMetaAnnotations(AnnotatedElement target, Set<Annotation> visited) {
    return Stream.of(target.getDeclaredAnnotations())
            .filter(Reflect::notJavaLangAnnotation)
            .flatMap(anno -> allMetaAnnotations(anno, visited));
  }

  private static Stream<Annotation> allMetaAnnotations(Annotation target, Set<Annotation> visited) {
    if (visited.add(target)) {
      return MoreStreams.prepend(target,
                                 Stream.of(target.annotationType().getDeclaredAnnotations())
                                         .flatMap(anno -> allMetaAnnotations(anno, visited))
      );
    } else {
      return Stream.empty();
    }
  }

  private static boolean notJavaLangAnnotation(Annotation annotation) {
    return !annotation.annotationType().getName().startsWith("java.lang.annotation.");
  }

  private static Stream<Class<?>> collectInterfaces(Class<?> type, Set<Class<?>> visited) {
    return visited.add(type)
            ? MoreStreams.prepend(type, Stream.of(type.getInterfaces())
                    .flatMap(i -> collectInterfaces(i, visited)))
            : Stream.empty();
  }
}
