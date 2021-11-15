package upstart.b4;

import upstart.config.ConfigMappingException;
import upstart.util.Optionals;

public class InvalidB4TargetException extends ConfigMappingException {
  public InvalidB4TargetException(TargetName targetName) {
    this(targetName, null);
  }

  public InvalidB4TargetException(TargetName targetName, Throwable cause) {
    super("Invalid target: '" + targetName.displayName() + "'", cause);
  }

  public static InvalidB4TargetException wrap(TargetName targetName, Throwable cause) {
    return Optionals.asInstance(cause, InvalidB4TargetException.class)
            .orElseGet(() -> new InvalidB4TargetException(targetName, cause));
  }
}
