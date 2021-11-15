package upstart.test;

import upstart.config.annotations.ConfigPath;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Arranges to provide an ephemeral in-memory {@link FileSystem} (implemented by {@link com.google.common.jimfs.Jimfs})
 * for parameter-injection into test-methods, as well as into any upstart-managed components that {@link Inject @Inject FileSystem}
 * (via {@link UpstartTest}/{@link UpstartTestBuilder}.
 * <p/>
 * Finally, arranges for any {@link Path} members in injected {@link ConfigPath @ConfigPath}-managed objects
 * (when mapped by the default {@link io.upstartproject.hojack.HojackConfigMapper}) to be
 * bound to this FileSystem.
 * <p/>
 * IMPORTANT: Note that the substitution of this fake filesystem is not comprehensive: only interactions with {@link Path} instances
 * that were obtained from this {@link FileSystem} (either directly, via {@link FileSystem#getPath}, or indirectly, for
 * example via {@link Path#resolve}, etc) are affected. Any use of {@link Paths#get}, {@link FileSystems#getDefault},
 * the legacy {@link java.io.File} class, or external {@link Process processes} will <strong>NOT</strong> use this FileSystem.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(FakeFileSystem.class)
public @interface FakeFileSystemTest {
  /**
   * The working-directory from which to compute {@link Path#toAbsolutePath} results for relative paths.
   * <p/>
   * Set this to the special value "<code>$PWD</code>" to use the actual working-directory of the running JVM process,
   * as determined by <code>System.getProperty("user.dir")</code>.
   */
  String workingDirectory() default "/work";
  long watchServicePollIntervalMs() default 1000;
}
