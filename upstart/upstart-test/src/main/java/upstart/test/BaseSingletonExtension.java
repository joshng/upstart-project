package upstart.test;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Type;

public abstract class BaseSingletonExtension<T> implements SingletonExtension<T> {
  private final Class<T> contextClass;


  public BaseSingletonExtension(Class<T> contextClass) {
    this.contextClass = contextClass;
  }

  @Override
  public Class<T> contextClass() {
    return contextClass;
  }

  public static <T> T getOrCreateContextFrom(
          Class<? extends SingletonExtension<T>> extensionClass,
          ExtensionContext extensionContext
  ) {
    return SingletonExtension.getOrCreateContextFrom(extensionClass, extensionContext);
  }
}
