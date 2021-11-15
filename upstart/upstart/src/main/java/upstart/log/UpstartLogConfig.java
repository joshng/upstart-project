package upstart.log;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import upstart.config.annotations.ConfigPath;

import java.util.Map;

/**
 * This upstart-config structure conveys implementation-agnostic log-configuration directives, to be interpreted and
 * applied at startup-time by the active {@link UpstartLogProvider} implementation discovered on the classpath.<p/>
 *
 * It supports abstract configuration of:
 * <ul>
 *   <li>a {@link LogThreshold} to be applied to the {@link #rootLogger} (ie, a default threshold)</li>
 *   <li>{@link LogThreshold LogThresholds} to be applied to specific log categories named as the keys in the {@link #levels}
 *       dictionary</li>
 *   <li>{@link UpstartLogProvider}-implementation-specific settings for {@link #appenders}</li>
 *   <li>arbitrary {@link #context} metadata to be attached to emitted logs</li>
 * </ul>
 *
 * The ultimate effect of this configuration is subject to interpretation by the {@link UpstartLogProvider}.
 */
@ConfigPath("upstart.log")
public abstract class UpstartLogConfig {
  public static ImmutableUpstartLogConfig.Builder builder() {
    return ImmutableUpstartLogConfig.builder();
  }

  private static UpstartLogConfig _lastAppliedConfig = null;

  public abstract LogThreshold rootLogger();

  public abstract Map<String, LogThreshold> levels();

  public abstract Map<String, AppenderConfig> appenders();

  public abstract Map<String, Object> context();

  public void apply() {
    UpstartLogProvider.CLASSPATH_PROVIDER.ifPresent(classpathProvider -> {
      synchronized (UpstartLogConfig.class) {
        if (!this.equals(_lastAppliedConfig)) {
          classpathProvider.applyLogConfig(this);
          _lastAppliedConfig = this;
        }
      }
    });
  }

  @SuppressWarnings("unused")
  public enum LogThreshold {
    OFF,
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE,
    ALL;

    @JsonCreator
    public static LogThreshold parse(String level) {
      return valueOf(level.toUpperCase());
    }
  }

  /**
   * AppenderConfig is an empty marker-interface to hold the JsonTypeInfo directive for polymorphic config-loading.
   * The specific "kinds" present in the config must be compatible with the active {@link UpstartLogProvider}
   * implementation.<p/>
   *
   * To discover the available types of {@link AppenderConfig}, search for implementations of this interface.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  public interface AppenderConfig {
  }
}
