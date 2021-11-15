package upstart.commandExecutor;

import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class CapturingStreamConsumer extends CommandSpec.StreamConsumer {
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @Override
  protected void consumeOutput(InputStream stream) throws IOException {
    ByteStreams.copy(stream, output);
  }

  String getOutput() {
    return output.toString();
  }
}
