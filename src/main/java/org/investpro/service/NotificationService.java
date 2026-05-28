package org.investpro.service;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.EmailNotifier;
import org.investpro.core.NotificationMessage;
import org.investpro.core.TelegramNotifier;
import org.investpro.core.agents.AgentEvent;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central notification service for InvestPro / investpro.
 *
 * Converts important agent/runtime events into user-facing notifications.
 *
 * Supports:
 * - Telegram
 * - Email
 * - Both
 * - None
 *
 * Also supports channel-specific email subscriptions, for example:
 * - OANDA ORDER_FILLED
 * - OANDA ORDER_REJECTED
 * - OANDA STREAM_DISCONNECTED
 * - OANDA RISK_REJECTED
 */
@Slf4j
public class NotificationService {

    private final TelegramNotifier telegramNotifier;
    private final EmailNotifier emailNotifier;

    /**
     * Default globally important events.
     */
    private final Set<String> importantEvents = Set.of(
            AgentEvent.SMART_BOT_STARTED,
            AgentEvent.SMART_BOT_STREAMING_STARTED,
            AgentEvent.SMART_BOT_STREAMING_STOPPED,
            AgentEvent.AUTO_TRADING_ENABLED,
            AgentEvent.AUTO_TRADING_DISABLED,
            AgentEvent.AI_REASONING_ENABLED,
            AgentEvent.AI_REASONING_DISABLED,
            AgentEvent.SIGNAL_CREATED,
            AgentEvent.STRATEGY_SIGNAL_APPROVED,
            AgentEvent.STRATEGY_SIGNAL_REJECTED,
            AgentEvent.RISK_APPROVED,
            AgentEvent.RISK_REJECTED,
            AgentEvent.REASONING_APPROVED,
            AgentEvent.REASONING_REJECTED,
            AgentEvent.ORDER_SUBMITTED,
            AgentEvent.ORDER_ACCEPTED,
            AgentEvent.ORDER_REJECTED,
            AgentEvent.ORDER_FILLED,
            AgentEvent.ORDER_CANCELLED,
            AgentEvent.POSITION_UPDATE,
            AgentEvent.BALANCE_UPDATE,
            AgentEvent.STREAM_DISCONNECTED,
            AgentEvent.ERROR
    );

    /**
     * Channel/exchange -> email recipient.
     *
     * Example:
     * OANDA -> trader@example.com
     */
    private final Map<String, String> emailRecipientsByChannel = new ConcurrentHashMap<>();

    /**
     * Channel/exchange -> subscribed event types.
     *
     * Example:
     * OANDA -> ORDER_FILLED, ORDER_REJECTED, RISK_REJECTED
     */
    private final Map<String, Set<String>> emailSubscriptionsByChannel = new ConcurrentHashMap<>();

    public NotificationService(TelegramNotifier telegramNotifier, EmailNotifier emailNotifier) {
        this.telegramNotifier = telegramNotifier;
        this.emailNotifier = emailNotifier;
    }

    public static NotificationService disabled() {
        return new NotificationService(null, null);
    }

    public boolean isTelegramEnabled() {
        return telegramNotifier != null && telegramNotifier.isEnabled();
    }

    public boolean isEmailEnabled() {
        return emailNotifier != null && emailNotifier.isEnabled();
    }

    public boolean isEnabled() {
        return isTelegramEnabled() || isEmailEnabled();
    }

    public void notify(NotificationMessage message) {
        if (message == null || message.isSilent()) {
            return;
        }

        if (!isEnabled()) {
            log.debug("Notification skipped because no notifier is enabled. title={}", message.title());
            return;
        }

        if (message.shouldSendTelegram()) {
            sendTelegram(message);
        }

        if (message.shouldSendEmail()) {
            sendEmail(message);
        }
    }

    public void notifyTelegram(String title, String body) {
        notify(NotificationMessage.info(title, body).toTelegramOnly());
    }

    public void notifyEmail(String title, String body) {
        notify(NotificationMessage.info(title, body).toEmailOnly());
    }

    @SuppressWarnings("unused")
    public void notifyBoth(String title, String body) {
        notify(NotificationMessage.info(title, body).toBoth());
    }

    /**
     * Notify from an agent event.
     *
     * This supports:
     * - default important events
     * - channel-specific email subscriptions such as OANDA events
     */
    @SuppressWarnings("unused")
    public void notifyAgentEvent(AgentEvent event) {
        if (event == null || event.type() == null) {
            return;
        }

        boolean globallyImportant = importantEvents.contains(event.type());
        boolean channelSubscribed = isSubscribedEmailEvent(event);

        if (!globallyImportant && !channelSubscribed) {
            return;
        }

        NotificationMessage message = toNotificationMessage(event);

        if (channelSubscribed) {
            String channel = extractChannel(event);
            String recipient = emailRecipientsByChannel.get(normalize(channel));

            if (recipient != null && !recipient.isBlank()) {
                message = message
                        .toEmailOnly()
                        .withMetadata("notificationChannel", channel)
                        .withMetadata("emailRecipient", maskEmail(recipient));
            }
        }

        notify(message);
    }

    private NotificationMessage toNotificationMessage(AgentEvent event) {
        String title = "InvestPro: %s".formatted(event.type());

        String body = """
                Source: %s
                Time: %s

                Payload:
                %s
                """.formatted(
                event.source(),
                event.timestamp(),
                payloadText(event.payload())
        );

        NotificationMessage message = switch (event.type()) {
            case AgentEvent.ERROR,
                 AgentEvent.STREAM_DISCONNECTED,
                 AgentEvent.ORDER_REJECTED,
                 AgentEvent.RISK_REJECTED,
                 AgentEvent.REASONING_REJECTED,
                 AgentEvent.STRATEGY_SIGNAL_REJECTED ->
                    NotificationMessage.warning(title, body);

            case AgentEvent.ORDER_FILLED,
                 AgentEvent.ORDER_ACCEPTED,
                 AgentEvent.ORDER_SUBMITTED ->
                    NotificationMessage.trade(title, body);

            case AgentEvent.SIGNAL_CREATED,
                 AgentEvent.STRATEGY_SIGNAL_APPROVED,
                 AgentEvent.RISK_APPROVED,
                 AgentEvent.REASONING_APPROVED ->
                    NotificationMessage.signal(title, body);

            default -> NotificationMessage.info(title, body);
        };

        return message
                .withMetadata("eventType", event.type())
                .withMetadata("eventSource", event.source())
                .withMetadata("eventTimestamp", event.timestamp())
                .withMetadata(event.metadata());
    }

    private void sendTelegram(NotificationMessage message) {
        if (!isTelegramEnabled()) {
            log.debug("Telegram notification skipped because TelegramNotifier is not enabled.");
            return;
        }

        try {
            boolean sent = telegramNotifier.sendMarkdown(message.toTelegramMarkdown());

            if (!sent) {
                log.warn("Telegram notification was not sent. title={}", message.title());
            }
        } catch (Exception exception) {
            log.warn("Telegram notification failed: {}", exception.getMessage(), exception);
        }
    }

    private void sendEmail(NotificationMessage message) {
        if (!isEmailEnabled()) {
            log.debug("Email notification skipped because EmailNotifier is not enabled.");
            return;
        }

        try {
            boolean sent = emailNotifier.send(message);

            if (!sent) {
                log.warn("Email notification was not sent. title={}", message.title());
            }
        } catch (Exception exception) {
            log.warn("Email notification failed: {}", exception.getMessage(), exception);
        }
    }

    private String payloadText(Object payload) {
        if (payload == null) {
            return "";
        }

        if (payload instanceof Throwable throwable) {
            return "%s: %s".formatted(
                    throwable.getClass().getSimpleName(),
                    Objects.toString(throwable.getMessage(), "")
            );
        }

        String text = String.valueOf(payload);

        if (text.length() > 1_500) {
            return "%s...".formatted(text.substring(0, 1_500));
        }

        return text;
    }

    /**
     * Register an email recipient for a notification channel/exchange.
     *
     * Example:
     * registerEmailRecipient("OANDA", "trader@example.com");
     */
    public void registerEmailRecipient(String channel, String emailAddress) {
        String normalizedChannel = normalize(channel);
        String email = safe(emailAddress);

        if (normalizedChannel.isBlank()) {
            log.warn("Cannot register email recipient: channel is blank");
            return;
        }

        if (!isValidEmail(email)) {
            log.warn(
                    "Cannot register email recipient for channel {}: invalid email={}",
                    normalizedChannel,
                    maskEmail(email)
            );
            return;
        }

        emailRecipientsByChannel.put(normalizedChannel, email);

        log.info(
                "Registered email recipient for channel {}: {}",
                normalizedChannel,
                maskEmail(email)
        );
    }

    /**
     * Subscribe a channel/exchange to receive email notifications for an event.
     *
     * Example:
     * subscribeEmail("OANDA", AgentEvent.ORDER_FILLED);
     */
    public void subscribeEmail(String channel, String eventType) {
        String normalizedChannel = normalize(channel);
        String normalizedEventType = safe(eventType);

        if (normalizedChannel.isBlank()) {
            log.warn("Cannot subscribe email event: channel is blank");
            return;
        }

        if (normalizedEventType.isBlank()) {
            log.warn("Cannot subscribe email event for channel {}: event type is blank", normalizedChannel);
            return;
        }

        emailSubscriptionsByChannel
                .computeIfAbsent(normalizedChannel, ignored -> ConcurrentHashMap.newKeySet())
                .add(normalizedEventType);

        log.info(
                "Subscribed channel {} to email event {}",
                normalizedChannel,
                normalizedEventType
        );
    }

    public void unsubscribeEmail(String channel, String eventType) {
        String normalizedChannel = normalize(channel);
        String normalizedEventType = safe(eventType);

        Set<String> events = emailSubscriptionsByChannel.get(normalizedChannel);

        if (events == null) {
            return;
        }

        events.remove(normalizedEventType);

        if (events.isEmpty()) {
            emailSubscriptionsByChannel.remove(normalizedChannel);
        }

        log.info(
                "Unsubscribed channel {} from email event {}",
                normalizedChannel,
                normalizedEventType
        );
    }

    public boolean isEmailSubscribed(String channel, String eventType) {
        String normalizedChannel = normalize(channel);
        String normalizedEventType = safe(eventType);

        return emailSubscriptionsByChannel
                .getOrDefault(normalizedChannel, Collections.emptySet())
                .contains(normalizedEventType);
    }

    public Set<String> getEmailSubscriptions(String channel) {
        String normalizedChannel = normalize(channel);

        return Set.copyOf(
                emailSubscriptionsByChannel.getOrDefault(normalizedChannel, Collections.emptySet())
        );
    }

    public String getEmailRecipient(String channel) {
        return emailRecipientsByChannel.get(normalize(channel));
    }

    private boolean isSubscribedEmailEvent(AgentEvent event) {
        String channel = extractChannel(event);

        if (channel.isBlank()) {
            return false;
        }

        return isEmailSubscribed(channel, event.type());
    }

    /**
     * Extract channel/exchange from event metadata.
     *
     * Supports metadata keys:
     * - exchange
     * - broker
     * - channel
     *
     * Falls back to source when source contains OANDA.
     */
    private String extractChannel(AgentEvent event) {
        if (event == null) {
            return "";
        }

        if (event.metadata() != null) {
            Object exchange = event.metadata().get("exchange");
            if (exchange != null && !String.valueOf(exchange).isBlank()) {
                return String.valueOf(exchange);
            }

            Object broker = event.metadata().get("broker");
            if (broker != null && !String.valueOf(broker).isBlank()) {
                return String.valueOf(broker);
            }

            Object channel = event.metadata().get("channel");
            if (channel != null && !String.valueOf(channel).isBlank()) {
                return String.valueOf(channel);
            }
        }

        String source = safe(event.source());

        if (source.toLowerCase(Locale.ROOT).contains("oanda")) {
            return "OANDA";
        }

        return "";
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private String normalize(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "";
        }

        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];

        if (name.length() <= 2) {
            return "**@" + domain;
        }

        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }
}