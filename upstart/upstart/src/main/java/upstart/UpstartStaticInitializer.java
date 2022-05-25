package upstart;

import org.kohsuke.MetaInfServices;
import upstart.config.UpstartEnvironment;
import upstart.util.Ambiance;
import upstart.util.concurrent.Threads;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * A base-class for services which must be initialized ASAP at system-startup. Subclasses must be registered via
 * META-INF/services/upstart.UpstartStaticInitializer (eg, with {@link MetaInfServices @MetaInfServices(UpstartStaticInitializer.class)}),
 * and may optionally override {@link #initialize} to perform logic at startup (although an equivalent `static` initializer
 * in the class is usually sufficient).
 * <p/>
 * Example:
 * <pre>{@code
 * @MetaInfServices(UpstartStaticInitializer.class)
 * class MyStaticInitializer extends UpstartStaticInitializer {
 *   static {
 *     // perform some one-time static initialization
 *   }
 * }
 * }</pre>
 */
public abstract class UpstartStaticInitializer {
  public static final String DEFAULT_DEV_ENVIRONMENT_NAME = "DEV";

  static {
    prepareIntellijDevEnvironment();
    ServiceLoader.load(UpstartStaticInitializer.class)
            .forEach(UpstartStaticInitializer::initialize);
  }


  public static void ensureInitialized() {
    // nothing to do now, handled by static-initializer above
  }

  public void initialize() {

  }

  public static void prepareIntellijDevEnvironment() {
    if (inferIntellijDevEnvironment()) {
      List<StackTraceElement> stackTrace = Threads.currentStackTrace();
      AtomicBoolean first = new AtomicBoolean(true);
      System.err.printf(
              """
              ******************************************************************************************
              ***  %s: Detected IntelliJ runtime, assuming %s=%s
              ***     at %s
              ******************************************************************************************
              """,
              UpstartStaticInitializer.class.getSimpleName(),
              UpstartEnvironment.UPSTART_ENVIRONMENT,
              DEFAULT_DEV_ENVIRONMENT_NAME,
              stackTrace.stream().skip(1)
                      .filter(ste -> first.getAndSet(false) || !ste.getClassName().startsWith("upstart"))
                      .limit(3)
                      .map(Object::toString)
                      .collect(Collectors.joining("\n***     at "))
      );

      System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, DEFAULT_DEV_ENVIRONMENT_NAME);
    }
  }

  public static boolean inferIntellijDevEnvironment() {
    return Ambiance.ambientValue(UpstartEnvironment.UPSTART_ENVIRONMENT).isEmpty()
            && ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .anyMatch(s -> s.contains("JetBrains") || s.contains("IntelliJ"));
  }
}
