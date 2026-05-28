package org.investpro.monitoring;

import lombok.Getter;

import java.time.Instant;

/**
 * Represents a system alert or notification.
 */

public record SystemAlert(
        AlertSeverity severity,
        String source,
        String message,
        Instant timestamp) {

    public enum AlertSeverity {
        DEBUG("#94a3b8"), // Gray
        INFO("#38bdf8"), // Blue
        WARNING("#f59e0b"), // Orange
        CRITICAL("#ef4444"); // Red

        private final String colorHex;

        AlertSeverity(String colorHex) {
            this.colorHex = colorHex;
        }

    }

    public static SystemAlert debug(String source, String message) {
        return new SystemAlert(AlertSeverity.DEBUG, source, message, Instant.now());
    }

    public static SystemAlert info(String source, String message) {
        return new SystemAlert(AlertSeverity.INFO, source, message, Instant.now());
    }

    public static SystemAlert warning(String source, String message) {
        return new SystemAlert(AlertSeverity.WARNING, source, message, Instant.now());
    }

    public static SystemAlert critical(String source, String message) {
        return new SystemAlert(AlertSeverity.CRITICAL, source, message, Instant.now());
    }
}
