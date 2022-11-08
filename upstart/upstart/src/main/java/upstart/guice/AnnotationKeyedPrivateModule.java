package upstart.guice;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class AnnotationKeyedPrivateModule extends UpstartPrivateModule {
  private final Annotation annotation;
  private final Type[] exposedTypes;

  public AnnotationKeyedPrivateModule(Annotation annotation, Type... exposedTypes) {
    this.annotation = annotation;
    this.exposedTypes = exposedTypes;
  }

  protected void configurePrivateScope() {

  }


  public <T> Key<T> annotatedKey(Class<T> type) {
    return Key.get(type, annotation);
  }

  public  Key<?> annotatedKey(Type type) {
    return Key.get(type, annotation);
  }

  public <T> Key<T> annotatedKey(TypeLiteral<T> type) {
    return Key.get(type, annotation);
  }

  public <T> Key<T> annotatedKey(Key<T> key) {
    return key.withAnnotation(annotation);
  }

  protected void exposeWithAnnotatedKey(Type boundType) {
    exposeWithAnnotatedKey(Key.get(boundType));
  }

  protected void exposeWithAnnotatedKey(TypeLiteral<?> boundType) {
    exposeWithAnnotatedKey(Key.get(boundType));
  }

  protected <T> void exposeWithAnnotatedKey(Key<T> privateKey) {
    Key<T> annotatedKey = annotatedKey(privateKey);
    binder().skipSources(AnnotationKeyedPrivateModule.class).bind(annotatedKey).to(privateKey);
    expose(annotatedKey);
  }

  protected ScopedBindingBuilder bindPrivateBindingToAnnotatedKey(Type boundType) {
    return bindToAnnotatedKey(privateBindingKey(boundType));
  }

  protected ScopedBindingBuilder bindPrivateBindingToAnnotatedKey(TypeLiteral<?> boundType) {
    return bindToAnnotatedKey(privateBindingKey(boundType));
  }

  protected <T> ScopedBindingBuilder bindToAnnotatedKey(Key<T> boundKey) {
    return binder().skipSources(AnnotationKeyedPrivateModule.class).bind(boundKey).to(annotatedKey(boundKey));
  }


  @Override
  protected final void configure() {
    configurePrivateScope();

    bindPrivateBinding(Annotation.class).toInstance(annotation);
    for (Type exposedType : exposedTypes) {
      exposeWithAnnotatedKey(exposedType);
    }
  }
}
