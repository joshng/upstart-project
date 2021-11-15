package upstart.javalin;

import io.javalin.core.JavalinConfig;

@FunctionalInterface
public interface JavalinWebInitializer {
  void initializeWeb(JavalinConfig config);
}
