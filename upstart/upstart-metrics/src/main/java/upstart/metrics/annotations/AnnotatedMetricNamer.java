package upstart.metrics.annotations;

import com.google.common.collect.ImmutableMap;
import upstart.metrics.TaggedMetricName;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartConfigProvider;
import upstart.util.MoreStrings;
import upstart.util.Reflect;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class AnnotatedMetricNamer {
  private static final Pattern VARIABLE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]*)}");
  private final UpstartConfigProvider config;

  @Inject
  public AnnotatedMetricNamer(UpstartApplicationConfig config) {
    this.config = config.provider();
  }

  private static String computeTagValue(MetricTag anno, UpstartConfigProvider config) {
    // I don't know why this Config-based solution did't perform interpolation..?
    // return ConfigFactory.parseMap(ImmutableMap.of(PRIVATE_METRIC_NAME, value)).withFallback(config).resolve().getString(PRIVATE_METRIC_NAME);
    return MoreStrings.interpolateTokens(
            anno.value(),
            VARIABLE_INTERPOLATION_PATTERN,
            matcher -> config.getString(matcher.group(1))
    );
  }

  public <A extends Annotation> String buildMetricName(Class<?> interceptedClass, Method method, Class<A> annotationClass, Function<A, String> annotationValueMethod) {
    String annotationValue = annotationValueMethod.apply(checkNotNull(method.getAnnotation(annotationClass), "Missing annotation", annotationClass, method));
    MetricTag[] annotationTags = method.getAnnotationsByType(MetricTag.class);
    String name = annotationValue.isEmpty()
            ? Reflect.getUnenhancedClass(interceptedClass).getName().replaceAll("\\$+", ".") + "." + method.getName()
            : annotationValue;
    Map<String, String> tags = Stream.of(annotationTags)
            .collect(ImmutableMap.toImmutableMap(MetricTag::key, anno -> computeTagValue(anno, config)));
    return TaggedMetricName.encodedName(name, tags);
  }
}
