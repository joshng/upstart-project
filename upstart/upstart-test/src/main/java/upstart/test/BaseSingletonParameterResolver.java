package upstart.test;

public abstract class BaseSingletonParameterResolver<T> extends BaseSingletonExtension<T> implements SingletonParameterResolver<T> {
  protected BaseSingletonParameterResolver(Class<T> paramClass) {
    super(paramClass);
  }
}
