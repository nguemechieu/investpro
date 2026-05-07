package org.investpro.exchange.models;

import lombok.Builder;
import lombok.Value;
import java.time.Instant;

/**
 * Result of placing an order on an exchange.
 *
 * <p>Captures order confirmation details and any errors that occurred during submission.
 */
@Value
@Builder(toBuilder = true)
public class OrderResult {
    boolean success;

    // On success
    String orderId; // Internal order ID
    String exchangeOrderId; // Exchange's order ID
    String status; // E.g., "PENDING", "FILLED", "PARTIAL_FILL"
    double filledSize;
    double filledPrice;

    // On failure
    String errorCode; // E.g., "HTTP_400", "INSUFFICIENT_FUNDS", "ORDER_REJECTED"
    String message;
    int httpStatus;

    Instant timestamp;
}
