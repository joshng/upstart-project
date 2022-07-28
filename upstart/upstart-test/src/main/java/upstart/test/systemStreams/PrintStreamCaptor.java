package upstart.test.systemStreams;

import upstart.util.SelfType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

public class PrintStreamCaptor<S extends PrintStreamCaptor<S>> implements AutoCloseable, SelfType<S> {
  protected final Supplier<PrintStream> getter;
  protected final Consumer<PrintStream> setter;
  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  private PrintStream realOut;
  private PrintStream outStream;

  public PrintStreamCaptor(Supplier<PrintStream> getter, Consumer<PrintStream> setter) {
    this.getter = getter;
    this.setter = setter;
  }

  public synchronized S startCapture() {
    if (!isCapturing()) {
      realOut = getter.get();
      outStream = new PrintStream(outputStream);
      System.setOut(outStream);
    }
    return self();
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

  @Override
  public void close() {
    endCapture();
  }
}
