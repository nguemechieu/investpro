package org.investpro.monitoring;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Logback Appender that captures log events and broadcasts them to listeners.
 * Broadcasts log events to any UI surface that wants real-time log data.
 *
 * Register listener via: ActivityLogAppender.getInstance().addListener(...)
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
public class ActivityLogAppender extends AppenderBase<ILoggingEvent> {
    private static final ActivityLogAppender INSTANCE = new ActivityLogAppender();
    private final CopyOnWriteArrayList<Consumer<LogEventData>> listeners = new CopyOnWriteArrayList<>();

    public static ActivityLogAppender getInstance() {
        return INSTANCE;
    }

    /**
     * Register a listener to receive log events
     */
    public void addListener(Consumer<LogEventData> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener
     */
    public void removeListener(Consumer<LogEventData> listener) {
        listeners.remove(listener);
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            String severity = event.getLevel().toString();
            String loggerName = event.getLoggerName();
            String message = event.getFormattedMessage();
            Instant timestamp = Instant.ofEpochMilli(event.getTimeStamp());

            LogEventData logEvent = new LogEventData(severity, loggerName, message, timestamp);

            // Notify all listeners
            for (Consumer<LogEventData> listener : listeners) {
                try {
                    listener.accept(logEvent);
                } catch (Exception e) {
                    log.debug("Error notifying listener", e);
                }
            }
        } catch (Exception e) {
            log.debug("Error in ActivityLogAppender", e);
        }
    }

    /**
     * Log event data transmitted to listeners
     */
    public record LogEventData(
            String severity,
            String loggerName,
            String message,
            Instant timestamp) {
    }
}
