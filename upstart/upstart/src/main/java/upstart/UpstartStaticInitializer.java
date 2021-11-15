package upstart;

import org.kohsuke.MetaInfServices;

import java.util.ServiceLoader;

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
  static {
    ServiceLoader.load(UpstartStaticInitializer.class)
            .forEach(UpstartStaticInitializer::initialize);
  }

  public static void ensureInitialized() {
    // nothing to do now, handled by static-initializer above
  }

  public void initialize() {

  }
}
