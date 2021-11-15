package upstart.util;

import java.util.Optional;

/**
 * Simple utilities for reading values from environment variables and/or system properties
 */
public interface Ambiance {
    static Optional<String> ambientValue(String key) {
        // system-properties are only used for tests. for security, env-vars take precedence.
        String env = System.getenv(key);
        return Optional.ofNullable(env != null ? env : System.getProperty(key));
    }

    static String requiredAmbientValue(String key) {
        return ambientValue(key).orElseThrow(() -> new IllegalStateException("Missing environment value: " + key));
    }
}

