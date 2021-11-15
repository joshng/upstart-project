package upstart.log4j;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.config.ObjectMapperFactory;
import upstart.config.annotations.DeserializedImmutable;
import upstart.log.UpstartLogConfig;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Layout;
import org.immutables.value.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

@DeserializedImmutable
@JsonDeserialize(as = ImmutableConsoleLog4jAppenderConfig.class)
@JsonTypeName("console")
public abstract class ConsoleLog4jAppenderConfig implements Log4jAppenderConfig {
  abstract LayoutConfig layout();

  @Override
  public Appender configureAppender(String name, UpstartLogConfig config) {
    Layout layout = layout().configureLayout(config);
    ConsoleAppender appender = new ConsoleAppender(layout);
    appender.activateOptions();
    // appender.setFollow(true) inappropriately closes stdout! just use a custom writer that has the same effect
    appender.setWriter(new OutputStreamWriter(SystemOutStream.INSTANCE));
    return appender;
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  interface LayoutConfig {
    Layout configureLayout(UpstartLogConfig config);
  }

  @Value.Immutable(singleton = true)
  @JsonDeserialize(as = ImmutableJsonLayoutConfig.class)
  @JsonTypeName("json")
  static abstract class JsonLayoutConfig implements LayoutConfig {
    @Override
    public Layout configureLayout(UpstartLogConfig config) {
      JsonLog4jLayout layout = new JsonLog4jLayout(ObjectMapperFactory.buildAmbientObjectMapper(), config);
      layout.activateOptions();
      return layout;
    }
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutablePatternLayoutConfig.class)
  @JsonTypeName("pattern")
  static abstract class PatternLayoutConfig implements LayoutConfig {
    abstract String pattern();
    @Override
    public Layout configureLayout(UpstartLogConfig config) {
      Layout layout = new EnhancedPatternLayout(pattern());
      layout.activateOptions();
      return layout;
    }
  }

  public static class SystemOutStream extends OutputStream {
    public static final SystemOutStream INSTANCE = new SystemOutStream();

    public void close() {
    }

    public void flush() {
      System.out.flush();
    }

    public void write(final byte[] b) throws IOException {
      System.out.write(b);
    }

    public void write(final byte[] b, final int off, final int len) {
      System.out.write(b, off, len);
    }

    public void write(final int b) {
      System.out.write(b);
    }
  }
}

