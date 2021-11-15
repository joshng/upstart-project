package upstart.test;

import com.google.common.collect.Lists;
import upstart.util.Reflect;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ExtensionContexts {
  /**
   * Finds the most applicable instance of the given annotation-type that applies to the current test, according to
   * junit-jupiter's resolution semantics, which could be from:
   * <ul>
   *   <li>the test-method (or its meta-annotations)</li>
   *   <li>the test-class (or its meta-annotations)</li>
   *   <li>test-class superclasses (or their meta-annotations)</li>
   *   <li>test-class interfaces (or their meta-annotations)</li>
   *   <li>for {@link Nested} tests: enclosing classes or their superclasses or interfaces  (or their meta-annotations)</li>
   * </ul>
   *
   * This is useful for {@link Extension}s that offer annotation-based configuration parameters.
   */
  public static <A extends Annotation> Optional<A> findNearestAnnotation(Class<A> annotationClass, ExtensionContext context) {
    return findTestAnnotations(annotationClass, Reflect.LineageOrder.SubclassBeforeSuperclass, context)
            .findFirst();
  }

  public static <A extends Annotation> Stream<A> findTestAnnotations(Class<A> annotationClass, Reflect.LineageOrder order, ExtensionContext context) {
    Stream<AnnotatedElement> annotationCandidates = testContextAnnotatedElements(context, order);
    return annotationCandidates.flatMap(element -> AnnotationSupport.findAnnotation(element, annotationClass).stream());
  }

  public static <A extends Annotation> Stream<A> findRepeatableTestAnnotations(Class<A> annotationClass, Reflect.LineageOrder order, ExtensionContext context) {
    Stream<AnnotatedElement> annotationCandidates = testContextAnnotatedElements(context, order);
    return annotationCandidates.flatMap(element -> {
      List<A> found = AnnotationSupport.findRepeatableAnnotations(element, annotationClass);
      if (order == Reflect.LineageOrder.SubclassBeforeSuperclass) found = Lists.reverse(found);
      return found.stream();
    });
  }

  public static Stream<AnnotatedElement> testContextAnnotatedElements(ExtensionContext context, Reflect.LineageOrder order) {
    if (order == Reflect.LineageOrder.SubclassBeforeSuperclass) {
      Stream<AnnotatedElement> testInstanceClasses = context.getTestInstances()
              .map(testInstances -> Lists.reverse(testInstances.getAllInstances()).stream()
                      .<AnnotatedElement>map(Object::getClass))
              .orElse(Stream.empty());
      return Stream.concat(context.getElement().stream(), testInstanceClasses);
    } else {
      Stream<AnnotatedElement> testInstanceClasses = context.getTestInstances()
              .map(testInstances -> testInstances.getAllInstances().stream()
                      .<AnnotatedElement>map(Object::getClass))
              .orElse(Stream.empty());
      return Stream.concat(testInstanceClasses, context.getElement().stream());
    }
  }

  public static List<Object> allNestedTestInstances(ExtensionContext context) {
    return context.getRequiredTestInstances().getAllInstances();
  }
}
