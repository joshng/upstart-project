package upstart.log4j.test;

import upstart.log.UpstartLogConfig;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import upstart.log4j.UpstartLog4jProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LogFixture {
  private static final Logger ROOT_LOGGER = LogManager.getRootLogger();
  private final Level rootLevel = ROOT_LOGGER.getLevel();
  private final Map<String, Optional<Level>> originalConfig = new HashMap<>();
  private final Set<SuppressLogs> appliedAnnotations = new HashSet<>();

  @SuppressWarnings("UnusedReturnValue")
  public LogFixture setRootThreshold(UpstartLogConfig.LogThreshold threshold) {
    ROOT_LOGGER.setLevel(UpstartLog4jProvider.toLevel(threshold));
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  public LogFixture setThreshold(Class<?> loggerClass, UpstartLogConfig.LogThreshold threshold) {
    return setThreshold(loggerClass.getName(), threshold);
  }

  @SuppressWarnings("UnusedReturnValue")
  public LogFixture setThreshold(String name, UpstartLogConfig.LogThreshold threshold) {
    Optional<Level> prev = setThreshold(name, Optional.of(UpstartLog4jProvider.toLevel(threshold)));
    originalConfig.putIfAbsent(name, prev);
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  public LogFixture apply(SuppressLogs annotation) {
    if (appliedAnnotations.add(annotation)) {
      UpstartLogConfig.LogThreshold threshold = annotation.threshold();
      if (annotation.value().length == 0 && annotation.categories().length == 0) {
        setRootThreshold(threshold);
      } else {
        for (String category : annotation.categories()) {
          setThreshold(category, threshold);
        }
        for (Class<?> c : annotation.value()) {
          setThreshold(c, threshold);
        }
      }
    }
    return this;
  }

  void revert() {
    ROOT_LOGGER.setLevel(rootLevel);
    originalConfig.forEach(this::setThreshold);
    appliedAnnotations.clear();
  }

  private Optional<Level> setThreshold(String logger, Optional<Level> threshold) {
    return setThreshold(LogManager.getLogger(logger), threshold);
  }

  private Optional<Level> setThreshold(Logger logger, Optional<Level> threshold) {
    Level prev = logger.getLevel();
    logger.setLevel(threshold.orElse(null));
    return Optional.ofNullable(prev);
  }
}
