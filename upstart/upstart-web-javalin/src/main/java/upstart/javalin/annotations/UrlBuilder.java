package upstart.javalin.annotations;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import upstart.util.strings.MoreStrings;
import upstart.util.strings.StringIdentifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class UrlBuilder {
  private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{(.+)}");
  private static final Joiner.MapJoiner QUERY_PARAM_JOINER = Joiner.on('&').withKeyValueSeparator('=');
  private final String urlTemplate;
  private Map<String, String> pathParams = null;
  private Map<String, String> queryParams = null;

  public UrlBuilder(String urlTemplate) {
    this.urlTemplate = urlTemplate;
  }

  public UrlBuilder withPathParam(String placeholder, Object value) {
    checkNotNull(value, placeholder);
    if (pathParams == null) pathParams = new HashMap<>();
    pathParams.put(placeholder, encode(value));
    return this;
  }

  public UrlBuilder withQueryParam(String key, Object value) {
    checkNotNull(value, key);
    if (queryParams == null) queryParams = new LinkedHashMap<>();
    queryParams.put(key, encode(value));
    return this;
  }

  public HttpUrl build() {
    StringBuilder builder = new StringBuilder();
    if (pathParams == null) {
      if (queryParams == null) {
        return HttpUrl.of(urlTemplate);
      }
      builder.append(urlTemplate);
    } else {
      MoreStrings.interpolateTokens(urlTemplate, TOKEN_PATTERN, builder, Functions.forMap(pathParams).compose(matcher -> matcher.group(1)));
    }
    if (queryParams != null) {
      QUERY_PARAM_JOINER.appendTo(builder.append('?'), queryParams);
    }
    return HttpUrl.of(builder.toString());
  }

  private String encode(Object value) {
    if (value instanceof StringIdentifier id) {
      value = id.value();
    } else if (value instanceof UrlParameter param) {
      value = param.urlValue();
    }
    return URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
  }
}
