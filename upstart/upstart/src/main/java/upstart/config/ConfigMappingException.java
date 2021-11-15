package upstart.config;

import java.util.Optional;

public class ConfigMappingException extends RuntimeException {
  public ConfigMappingException(String message) {
    super(message);
  }

  public ConfigMappingException(String message, Throwable cause) {
    super(Optional.ofNullable(cause).map(c -> c.getMessage() + "\n").orElse("") + message, cause);
  }
}
