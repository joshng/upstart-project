package upstart.test.systemStreams;

import static com.google.common.base.Preconditions.checkState;

/**
 * @see CaptureSystemOut
 */
public class SystemOutCaptor extends PrintStreamCaptor<SystemOutCaptor> {

  public SystemOutCaptor() {
    super(() -> System.out, System::setOut);
  }

  public static SystemOutCaptor start() {
    return new SystemOutCaptor().startCapture();
  }
}
