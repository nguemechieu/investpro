package org.investpro.investpro;

import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.Trade;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    // List to hold the subscribers (e.g., users who should receive notifications)
    private final List<String> subscribers;

    // Constructor initializes the list of subscribers
    public NotificationService() {
        this.subscribers = new ArrayList<>();
    }

    public static void sendTradeNotification(@NotNull Trade trade) {
        // Here you would implement the notification logic based on the trade event,
        // For example, you could log the trade event to a file or email the subscribers
        logger.info("Trade executed: {}", trade);
    }

    public static void sendCandleDataNotification(List<CandleData> candleData) {
        // Here you would implement the notification logic based on the candle data,
        // For example, you could log the candle data to a file or email the subscribers
        logger.info("Candle data received: {}", candleData);
    }

    // Subscribe a user to notifications
    public void subscribe(String subscriber) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber);
            logger.debug("Subscriber {} added to notification list.", subscriber);
        } else {
            logger.debug("Subscriber {} is already in the notification list.", subscriber);
        }
    }

    // Unsubscribe a user from notifications
    public void unsubscribe(String subscriber) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber);
            logger.debug("Subscriber {} removed from notification list.", subscriber);
        } else {
            logger.debug("Subscriber {} was not found in the notification list.", subscriber);
        }
    }

    // Notify all subscribers about a trade event
    public void notifyTrade(Trade trade) {
        for (String subscriber : subscribers) {
            sendNotification(subscriber, "Trade executed: " + trade.toString());
        }
    }

    // Notify all subscribers about a system alert or error
    public void notifySystemAlert(String message) {
        for (String subscriber : subscribers) {
            sendNotification(subscriber, "System Alert: " + message);
        }
    }

    // Private method to send a notification (could be via email, SMS, or in-app)
    private void sendNotification(String recipient, String message) {
        // In a real application, this might send an email, SMS, or push notification
        logger.info("Sending notification to {}: {}", recipient, message);
        // Example: EmailService.sendEmail(recipient, "Notification", message);
    }

    // Get a list of all subscribers
    public List<String> getSubscribers() {
        return new ArrayList<>(subscribers); // Return a copy to preserve encapsulation
    }
}
