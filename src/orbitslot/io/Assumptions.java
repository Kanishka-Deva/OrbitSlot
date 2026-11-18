package orbitslot.io;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Properties;

/**
 * Typed, read-only view of data/assumptions.properties.
 * A missing or malformed key fails immediately with a clear message, so a
 * typo in the file never silently becomes a default value.
 */
public final class Assumptions {

    private final Properties properties;

    public Assumptions(Properties properties) {
        this.properties = properties;
    }

    public String getString(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing key in assumptions.properties: " + key);
        }
        return value.trim();
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(getString(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Key " + key + " must be a whole number", e);
        }
    }

    public double getDouble(String key) {
        try {
            return Double.parseDouble(getString(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Key " + key + " must be a number", e);
        }
    }

    public long getLong(String key) {
        try {
            return Long.parseLong(getString(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Key " + key + " must be a whole number", e);
        }
    }

    public Instant getInstant(String key) {
        return Instant.parse(getString(key));
    }

    public ZoneId getZone(String key) {
        return ZoneId.of(getString(key));
    }
}
