package upstart.util;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.net.InetAddress;

/**
 * Applies a simplistic algorithm for guessing the hostname of the local host:
 * <p/>
 * If a HOSTNAME environment-variable is set, then use that.
 * <p/>
 * Otherwise, try to obtain the hostname using {@link InetAddress#getLocalHost}.{@link InetAddress#getHostName() getHostName}
 * (which relies on a reverse-DNS lookup, so not always reliable/accurate).
 */
@Value.Immutable
@JsonDeserialize(as = ImmutableLocalHost.class)
public interface LocalHost {
  static ImmutableLocalHost.Builder builder() {
    return ImmutableLocalHost.builder();
  }

  LocalHost INSTANCE = lookupLocal();

  String hostname();
  String ipAddress();

  private static LocalHost lookupLocal() {
    String hostname = System.getenv("HOSTNAME");
    String ip;

    try {
      InetAddress inetAddress = InetAddress.getLocalHost();
      if (hostname == null) hostname = inetAddress.getHostName();
      ip = inetAddress.getHostAddress();

    } catch (Exception e) {
      ip = "unknown_ip";
      if (hostname == null) hostname = "unknown_hostname";
    }

    return builder().hostname(hostname).ipAddress(ip).build();
  }

  static String getLocalHostname() {
    return INSTANCE.hostname();
  }

  static String getLocalIpAddress() {
    return INSTANCE.ipAddress();
  }
}
