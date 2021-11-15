package io.upstartproject.hojack;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationConfigDeserializer extends JsonDeserializer<Duration> {
  public static final Module JACKSON_MODULE = new SimpleModule()
          .addDeserializer(Duration.class, new DurationConfigDeserializer())
          .addSerializer(Duration.class, ToStringSerializer.instance);
  private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*(\\S+)");

  private static final Map<String, ChronoUnit> SUFFIXES;

  static {
    SUFFIXES = new HashMap<>();
    SUFFIXES.put("ns", ChronoUnit.NANOS);
    SUFFIXES.put("nanosecond", ChronoUnit.NANOS);
    SUFFIXES.put("nanoseconds", ChronoUnit.NANOS);
    SUFFIXES.put("us", ChronoUnit.MICROS);
    SUFFIXES.put("microsecond", ChronoUnit.MICROS);
    SUFFIXES.put("microseconds", ChronoUnit.MICROS);
    SUFFIXES.put("ms", ChronoUnit.MILLIS);
    SUFFIXES.put("millisecond", ChronoUnit.MILLIS);
    SUFFIXES.put("milliseconds", ChronoUnit.MILLIS);
    SUFFIXES.put("s", ChronoUnit.SECONDS);
    SUFFIXES.put("second", ChronoUnit.SECONDS);
    SUFFIXES.put("seconds", ChronoUnit.SECONDS);
    SUFFIXES.put("m", ChronoUnit.MINUTES);
    SUFFIXES.put("min", ChronoUnit.MINUTES);
    SUFFIXES.put("mins", ChronoUnit.MINUTES);
    SUFFIXES.put("minute", ChronoUnit.MINUTES);
    SUFFIXES.put("minutes", ChronoUnit.MINUTES);
    SUFFIXES.put("h", ChronoUnit.HOURS);
    SUFFIXES.put("hour", ChronoUnit.HOURS);
    SUFFIXES.put("hours", ChronoUnit.HOURS);
    SUFFIXES.put("d", ChronoUnit.DAYS);
    SUFFIXES.put("day", ChronoUnit.DAYS);
    SUFFIXES.put("days", ChronoUnit.DAYS);
  }

  @Override
  public Duration deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    return parse(jsonParser.getValueAsString());
  }

  public static Duration parse(String duration) {
    final Matcher matcher = DURATION_PATTERN.matcher(duration);
    if (matcher.matches()) {
      final ChronoUnit unit = SUFFIXES.get(matcher.group(2));
      if (unit != null) {
        final long count = Long.parseLong(matcher.group(1));
        return Duration.of(count, unit);
      }
    }
    // fall back to built-in Duration parsing (which requires the unfriendly ISO-8601 format -- "PT30S")
    return Duration.parse(duration);
  }
}

