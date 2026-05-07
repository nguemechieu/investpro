package org.investpro.service;

import lombok.extern.slf4j.Slf4j;

import org.investpro.core.agents.AgentEvent;
import org.investpro.core.EmailNotifier;

import org.investpro.core.NotificationMessage;
import org.investpro.core.TelegramNotifier;
import java.util.Objects;
import java.util.Set;

/**
 * Central notification service for InvestPro.
 *
 * Converts important agent/runtime events into user-facing notifications.
 *
 * Supports:
 * - Telegram
 * - Email
 * - Both
 * - None
 */
@Slf4j
public class NotificationService {
    private final TelegramNotifier telegramNotifier;
    private final EmailNotifier emailNotifier;

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

    @SuppressWarnings("unused")
    public void notifyAgentEvent(AgentEvent event) {
        if (event == null || !importantEvents.contains(event.type())) {
            return;
        }

        NotificationMessage message = toNotificationMessage(event);
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
}