package upstart.javalin.annotations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import upstart.util.annotations.Identifier;

import java.nio.file.Paths;

@Identifier
public abstract class HttpUrl {
  private static final String ROOT_PATH = "/";
  public static final HttpUrl ROOT = new HttpUrl() {
    @Override
    public String value() {
      return ROOT_PATH;
    }

    @Override
    public HttpUrl resolve(HttpUrl subPath) {
      return subPath.value().startsWith(ROOT_PATH) ? subPath : super.resolve(subPath.value());
    }

    @Override
    public HttpUrl resolve(String subPath) {
      return subPath.startsWith(ROOT_PATH) ? HttpUrl.of(subPath) : super.resolve(subPath);
    }
  };

  @JsonCreator
  public static HttpUrl of(String url) {
    return url.equals(ROOT.value()) ? ROOT : ImmutableHttpUrl.of(url);
  }

  @JsonValue
  public abstract String value();

  public HttpUrl resolve(HttpUrl subPath) {
    return resolve(subPath.value());
  }

  public HttpUrl resolve(String subPath) {
    return of(Paths.get(value(), subPath).toString());
  }

  @Override
  public String toString() {
    return value();
  }
}
