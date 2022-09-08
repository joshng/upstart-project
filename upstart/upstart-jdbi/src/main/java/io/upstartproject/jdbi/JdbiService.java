package io.upstartproject.jdbi;

import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import upstart.guice.PrivateBinding;
import upstart.util.concurrent.services.InitializingService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Set;

@Singleton
public class JdbiService extends InitializingService {
  private final JdbiInitializer initializer;
  private final Set<JdbiPlugin> plugins;
  private final Annotation bindingAnnotation;
  private Jdbi jdbi;

  @Inject
  public JdbiService(
          @PrivateBinding JdbiInitializer initializer,
          @PrivateBinding Set<JdbiPlugin> plugins,
          @PrivateBinding Annotation bindingAnnotation
          ) {
    this.initializer = initializer;
    this.plugins = plugins;
    this.bindingAnnotation = bindingAnnotation;
  }

  @Override
  protected void startUp() throws Exception {
    jdbi = initializer.buildJdbi();
    plugins.forEach(jdbi::installPlugin);
  }

  public Jdbi getJdbi() {
    return jdbi;
  }

  public <R, E, X extends Exception> R withExtension(Class<E> extensionType, ExtensionCallback<R, E, X> callback) throws X {
    return jdbi.withExtension(extensionType, callback);
  }

  public <E, X extends Exception> void useExtension(Class<E> extensionType, ExtensionConsumer<E, X> callback) throws X {
    jdbi.useExtension(extensionType, callback);
  }

  public <E> E onDemand(Class<E> extensionType) {
    return jdbi.onDemand(extensionType);
  }

  public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
    return jdbi.withHandle(callback);
  }

  public <X extends Exception> void useHandle(HandleConsumer<X> consumer) throws X {
    jdbi.useHandle(consumer);
  }

  public <R, X extends Exception> R inTransaction(HandleCallback<R, X> callback) throws X {
    return jdbi.inTransaction(callback);
  }

  public <X extends Exception> void useTransaction(HandleConsumer<X> callback) throws X {
    jdbi.useTransaction(callback);
  }

  public <R, X extends Exception> R inTransaction(TransactionIsolationLevel level, HandleCallback<R, X> callback) throws X {
    return jdbi.inTransaction(level, callback);
  }

  public <X extends Exception> void useTransaction(TransactionIsolationLevel level, HandleConsumer<X> callback) throws X {
    jdbi.useTransaction(level, callback);
  }

  public interface JdbiInitializer {
    Jdbi buildJdbi();
  }
}
