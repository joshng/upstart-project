package upstart.test;

import com.google.inject.Module;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.util.reflect.Reflect;

import java.lang.annotation.Annotation;
import java.util.function.Function;

import static upstart.util.functions.MoreFunctions.notEqual;

public interface UpstartTestInitializer {
  void initialize(UpstartTestBuilder testBuilder, ExtensionContext context);

  static <A extends Annotation> void installAnnotatedModule(
          Class<A> annotationType,
          Function<? super A, Class<? extends Module>> moduleAccessor,
          UpstartTestBuilder testBuilder,
          ExtensionContext context
  ) {
    ExtensionContexts.findTestAnnotations(annotationType, Reflect.LineageOrder.SubclassBeforeSuperclass, context)
            .map(moduleAccessor)
            .filter(notEqual(Module.class))
            .distinct()
            .forEach(testBuilder::installModule);
  }
}
