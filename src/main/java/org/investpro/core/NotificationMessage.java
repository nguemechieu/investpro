package org.investpro.core;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable notification payload used by Telegram/email/system notifiers.
 */
public record NotificationMessage(
        String title,
        String body,
        String severity,
        NotificationChannel channel,
        Instant timestamp,
        Map<String, Object> metadata
) {

    public NotificationMessage {
        title = safe(title);
        body = safe(body);
        severity = normalizeSeverity(severity);
        channel = channel == null ? NotificationChannel.BOTH : channel;
        timestamp = timestamp == null ? Instant.now() : timestamp;

        if (metadata == null || metadata.isEmpty()) {
            metadata = Map.of();
        } else {
            metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }

    public static NotificationMessage info(String title, String body) {
        return new NotificationMessage(
                title,
                body,
                "INFO",
                NotificationChannel.BOTH,
                Instant.now(),
                Map.of()
        );
    }

    @Contract("_, _ -> new")
    public static @NotNull NotificationMessage warning(String title, String body) {
        return new NotificationMessage(
                title,
                body,
                "WARNING",
                NotificationChannel.BOTH,
                Instant.now(),
                Map.of()
        );
    }

    public static NotificationMessage error(String title, String body) {
        return new NotificationMessage(
                title,
                body,
                "ERROR",
                NotificationChannel.BOTH,
                Instant.now(),
                Map.of()
        );
    }

    public static NotificationMessage trade(String title, String body) {
        return new NotificationMessage(
                title,
                body,
                "TRADE",
                NotificationChannel.BOTH,
                Instant.now(),
                Map.of()
        );
    }

    public static NotificationMessage signal(String title, String body) {
        return new NotificationMessage(
                title,
                body,
                "SIGNAL",
                NotificationChannel.BOTH,
                Instant.now(),
                Map.of()
        );
    }

    public NotificationMessage toTelegramOnly() {
        return withChannel(NotificationChannel.TELEGRAM);
    }

    public NotificationMessage toEmailOnly() {
        return withChannel(NotificationChannel.EMAIL);
    }

    public NotificationMessage toBoth() {
        return withChannel(NotificationChannel.BOTH);
    }

    public NotificationMessage silent() {
        return withChannel(NotificationChannel.NONE);
    }

    public NotificationMessage withChannel(NotificationChannel newChannel) {
        return new NotificationMessage(
                title,
                body,
                severity,
                newChannel == null ? NotificationChannel.BOTH : newChannel,
                timestamp,
                metadata
        );
    }

    public NotificationMessage withMetadata(String key, Object value) {
        if (key == null || key.isBlank()) {
            return this;
        }

        Map<String, Object> updated = new LinkedHashMap<>(metadata);
        updated.put(key, value);

        return new NotificationMessage(
                title,
                body,
                severity,
                channel,
                timestamp,
                updated
        );
    }

    public NotificationMessage withMetadata(Map<String, Object> extraMetadata) {
        if (extraMetadata == null || extraMetadata.isEmpty()) {
            return this;
        }

        Map<String, Object> updated = new LinkedHashMap<>(metadata);
        updated.putAll(extraMetadata);

        return new NotificationMessage(
                title,
                body,
                severity,
                channel,
                timestamp,
                updated
        );
    }

    public boolean isError() {
        return Objects.equals(severity, "ERROR");
    }

    public boolean isWarning() {
        return Objects.equals(severity, "WARNING");
    }

    public boolean isInfo() {
        return Objects.equals(severity, "INFO");
    }

    public boolean shouldSendTelegram() {
        return channel == NotificationChannel.TELEGRAM || channel == NotificationChannel.BOTH;
    }

    public boolean shouldSendEmail() {
        return channel == NotificationChannel.EMAIL || channel == NotificationChannel.BOTH;
    }

    public boolean isSilent() {
        return channel == NotificationChannel.NONE;
    }

    @Contract(pure = true)
    public @NotNull String toPlainText() {
        return """
                %s
                
                %s
                
                Severity: %s
                Channel: %s
                Time: %s
                """.formatted(
                title,
                body,
                severity,
                channel,
                timestamp
        );
    }

    public String toTelegramMarkdown() {
        return """
                *%s*
                
                %s
                
                Severity: `%s`
                Time: `%s`
                """.formatted(
                escapeMarkdown(title),
                escapeMarkdown(body),
                escapeMarkdown(severity),
                escapeMarkdown(String.valueOf(timestamp))
        );
    }

    private static String normalizeSeverity(String value) {
        String text = safe(value).toUpperCase();

        if (text.isBlank()) {
            return "INFO";
        }

        return text;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static @NotNull String escapeMarkdown(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`");
    }
}