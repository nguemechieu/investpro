package org.investpro.core;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Telegram Event Listener - Bridges SmartBot events to Telegram notifications.
 * <p>
 * Responsibilities:
 * - Subscribe to AgentEventBus for all events
 * - Listen for trading events (entry, exit, SL hit, TP hit)
 * - Listen for alerts (risk warnings, market moves)
 * - Listen for strategy signals
 * - Send appropriate Telegram notifications
 * <p>
 * Features:
 * - Event filtering by type and severity
 * - Rate limiting to avoid spam
 * - Error handling and recovery
 * - Detailed notification formatting
 */
@Slf4j
public class TelegramEventListener implements Consumer<AgentEvent> {
    private final TelegramNotifier telegramNotifier;
    private final AgentEventBus eventBus;

    private volatile boolean listening = false;
    private long lastNotificationTime = System.currentTimeMillis();
    private static final long MIN_NOTIFICATION_INTERVAL_MS = 500; // Prevent spam

    public TelegramEventListener(
            @NotNull AgentEventBus eventBus,
            @NotNull TelegramNotifier telegramNotifier) {
        this.eventBus = eventBus;
        this.telegramNotifier = telegramNotifier;
    }

    /**
     * Start listening to all agent events
     */
    public void start() {
        if (listening) {
            log.warn("TelegramEventListener is already listening");
            return;
        }

        try {
            // Subscribe to ALL agent events
            eventBus.subscribeAll(this);
            listening = true;
            log.info("✅ TelegramEventListener started - Telegram notifications enabled");
        } catch (Exception exception) {
            log.error("Failed to start TelegramEventListener", exception);
        }
    }

    /**
     * Stop listening to agent events
     * Note: AgentEventBus doesn't have an unsubscribe method,
     * so we just mark as not listening
     */
    public void stop() {
        if (!listening) {
            return;
        }

        listening = false;
        log.info("TelegramEventListener stopped");
    }

    /**
     * Main event consumer - routes events to appropriate handlers
     */
    @Override
    public void accept(AgentEvent event) {
        if (event == null || !telegramNotifier.isEnabled() || !telegramNotifier.hasTargetChat()) {
            return;
        }

        try {
            String eventType = event.type();
            Map<String, Object> metadata = event.metadata();
            String severity = (String) metadata.getOrDefault("severity", "INFO");
            String message = event.payload() instanceof String ? (String) event.payload() : "";

            // Check rate limiting
            if (!shouldSendNotification(severity)) {
                log.debug("Notification rate limited: {}", eventType);
                return;
            }

            // Route to appropriate handler
            String notification = switch (eventType) {
                case "ORDER_SUBMITTED" -> formatTradeEntryNotification(event);
                case "POSITION_CLOSED" -> formatTradeExitNotification(event);
                case "RISK_ALERT" -> formatRiskAlertNotification(event);
                case "MARKET_ALERT" -> formatMarketAlertNotification(event);
                case "STRATEGY_SIGNAL_APPROVED" -> formatStrategySignalNotification(event);
                case "PORTFOLIO_UPDATED" -> formatPortfolioUpdateNotification(event);
                case "ERROR" -> formatErrorNotification(event);
                default -> null;
            };

            if (notification != null && !notification.isBlank()) {
                sendNotification(notification, severity);
            }

        } catch (Exception exception) {
            log.error("Failed to process agent event", exception);
        }
    }

    /**
     * Get attribute from event metadata with fallback
     */
    private Object getAttribute(AgentEvent event, String key, Object defaultValue) {
        return event.metadata().getOrDefault(key, defaultValue);
    }

    /**
     * Get string attribute from event metadata
     */
    private String getStringAttribute(AgentEvent event, String key, String defaultValue) {
        Object value = getAttribute(event, key, defaultValue);
        return value instanceof String ? (String) value : defaultValue;
    }

    /**
     * Format trade entry notification
     */
    private String formatTradeEntryNotification(AgentEvent event) {
        return """
                📈 **TRADE ENTRY**

                Symbol: %s
                Direction: %s
                Entry Price: %s
                Quantity: %s
                Time: %s
                Reason: %s
                """.formatted(
                getStringAttribute(event, "symbol", "N/A"),
                getStringAttribute(event, "direction", "N/A"),
                getStringAttribute(event, "entryPrice", "N/A"),
                getStringAttribute(event, "quantity", "N/A"),
                event.timestamp(),
                event.payload() instanceof String ? event.payload() : "Order submitted");
    }

    /**
     * Format trade exit notification
     */
    private String formatTradeExitNotification(AgentEvent event) {
        String exitPrice = getStringAttribute(event, "exitPrice", "N/A");
        String pnl = getStringAttribute(event, "pnl", "N/A");
        String pnlPercent = getStringAttribute(event, "pnlPercent", "N/A");

        return """
                📉 **TRADE EXIT**

                Symbol: %s
                Exit Price: %s
                PnL: %s (%s)
                Duration: %s
                Reason: %s
                """.formatted(
                getStringAttribute(event, "symbol", "N/A"),
                exitPrice,
                pnl,
                pnlPercent,
                getStringAttribute(event, "duration", "N/A"),
                event.payload() instanceof String ? event.payload() : "Position closed");
    }

    /**
     * Format risk alert notification
     */
    private String formatRiskAlertNotification(AgentEvent event) {
        return """
                ⚠️ **RISK ALERT**

                Type: %s
                Current: %s
                Limit: %s
                Status: %s
                Action: %s
                """.formatted(
                getStringAttribute(event, "riskType", "N/A"),
                getStringAttribute(event, "current", "N/A"),
                getStringAttribute(event, "limit", "N/A"),
                getStringAttribute(event, "status", "N/A"),
                event.payload() instanceof String ? event.payload() : "Risk threshold exceeded");
    }

    /**
     * Format market alert notification
     */
    private String formatMarketAlertNotification(AgentEvent event) {
        return """
                🔔 **MARKET ALERT**

                Symbol: %s
                Alert Type: %s
                Condition: %s
                Current Value: %s
                Action: %s
                """.formatted(
                getStringAttribute(event, "symbol", "N/A"),
                getStringAttribute(event, "alertType", "N/A"),
                getStringAttribute(event, "condition", "N/A"),
                getStringAttribute(event, "currentValue", "N/A"),
                event.payload() instanceof String ? event.payload() : "Market condition detected");
    }

    /**
     * Format strategy signal notification
     */
    private String formatStrategySignalNotification(AgentEvent event) {
        return """
                🎲 **STRATEGY SIGNAL**

                Strategy: %s
                Symbol: %s
                Signal: %s
                Strength: %s
                Confidence: %s
                Action: %s
                """.formatted(
                getStringAttribute(event, "strategy", "N/A"),
                getStringAttribute(event, "symbol", "N/A"),
                getStringAttribute(event, "signal", "N/A"),
                getStringAttribute(event, "strength", "N/A"),
                getStringAttribute(event, "confidence", "N/A"),
                event.payload() instanceof String ? event.payload() : "Strategy signal generated");
    }

    /**
     * Format portfolio update notification
     */
    private String formatPortfolioUpdateNotification(AgentEvent event) {
        return """
                📊 **PORTFOLIO UPDATE**

                Total Value: %s
                Change: %s
                Open Positions: %s
                Win Rate: %s
                Active Strategy: %s
                """.formatted(
                getStringAttribute(event, "totalValue", "N/A"),
                getStringAttribute(event, "change", "N/A"),
                getStringAttribute(event, "openPositions", "N/A"),
                getStringAttribute(event, "winRate", "N/A"),
                getStringAttribute(event, "activeStrategy", "N/A"));
    }

    /**
     * Format error notification
     */
    private String formatErrorNotification(AgentEvent event) {
        return """
                ❌ **ERROR**

                Source: %s
                Error: %s
                Details: %s
                Time: %s
                """.formatted(
                getStringAttribute(event, "source", "N/A"),
                getStringAttribute(event, "errorType", "N/A"),
                event.payload() instanceof String ? event.payload() : "An error occurred",
                event.timestamp());
    }

    /**
     * Send notification with rate limiting and error handling
     */
    private void sendNotification(String notification, String severity) {
        try {
            // Add emoji prefix based on severity
            String prefix = switch (severity) {
                case "CRITICAL" -> "🔴";
                case "WARNING" -> "🟡";
                case "INFO" -> "🔵";
                default -> "⚪";
            };

            String formattedMessage = prefix + " " + notification;

            if (notification.contains("**")) {
                telegramNotifier.sendMarkdown(formattedMessage);
            } else {
                telegramNotifier.send(formattedMessage);
            }

            lastNotificationTime = System.currentTimeMillis();
        } catch (Exception exception) {
            log.error("Failed to send Telegram notification", exception);
        }
    }

    /**
     * Check if we should send notification based on rate limiting
     */
    private boolean shouldSendNotification(String severity) {
        // Always send critical/error notifications
        if ("CRITICAL".equals(severity) || "ERROR".equals(severity)) {
            return true;
        }

        // Rate limit other notifications
        long timeSinceLastNotification = System.currentTimeMillis() - lastNotificationTime;
        return timeSinceLastNotification >= MIN_NOTIFICATION_INTERVAL_MS;
    }

    /**
     * Check if listener is active
     */
    public boolean isListening() {
        return listening;
    }
}
