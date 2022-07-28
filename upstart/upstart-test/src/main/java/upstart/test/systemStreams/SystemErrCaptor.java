package upstart.test.systemStreams;

public class SystemErrCaptor extends PrintStreamCaptor<SystemErrCaptor> {

  public SystemErrCaptor() {
    super(() -> System.err, System::setErr);
  }

  public static SystemErrCaptor start() {
    return new SystemErrCaptor().startCapture();
  }
}
