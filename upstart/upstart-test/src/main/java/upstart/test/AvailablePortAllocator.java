package upstart.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class AvailablePortAllocator {
  /**
   * Avoid returning privileged port.
   */

  private static final int MIN_PORT_NUMBER = 1100;
  private static final int MAX_PORT_NUMBER = 49150;
  private static final AtomicInteger NEXT_PORT = new AtomicInteger(ThreadLocalRandom.current().nextInt(MIN_PORT_NUMBER, MAX_PORT_NUMBER + 1));

  public static int allocatePort() {
    int firstPort = nextPort();
    int port = firstPort;
    Exception error;
    do {
      try {
        new ServerSocket(port).close();
        return port;
      } catch (IOException e) {
        // must already be taken
        error = e;
      }
      port = nextPort();
    } while (port != firstPort);

    throw new IllegalStateException("Could not find available port in range " + MIN_PORT_NUMBER + " to " + MAX_PORT_NUMBER, error);
  }

  private static int nextPort() {
    return NEXT_PORT.getAndUpdate(x -> x < MAX_PORT_NUMBER ? x + 1 : MIN_PORT_NUMBER);
  }
}
