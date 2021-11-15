package upstart.test.systemStreams;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkState;

/**
 * @see CaptureSystemOut
 */
public class SystemOutCaptor {
  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  private PrintStream realOut;
  private PrintStream outStream;

  public synchronized void startCapture() {
    if (!isCapturing()) {
      realOut = System.out;
      outStream = new PrintStream(outputStream);
      System.setOut(outStream);
    }
  }

  public byte[] getCapturedBytes() {
    return outputStream.toByteArray();
  }

  public String getCapturedString() {
    return getCapturedString(StandardCharsets.UTF_8);
  }
  public String getCapturedString(Charset charset) {
    return new String(getCapturedBytes(), charset);
  }

  public void reset() {
    checkState(isCapturing(), "SystemOutCapture was not started");
    outStream.flush();
    outputStream.reset();
  }

  public synchronized void endCapture() {
    if (isCapturing()) {
      System.setOut(realOut);
      realOut = null;
    }
  }

  public synchronized boolean isCapturing() {
    return realOut != null;
  }
}
