package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Tracks an order that has been submitted to the broker but not yet
 * confirmed via a broker activity event.
 */
@Value
@Builder
public class PendingOrderConfirmation {
    String exchangeId;
    String accountId;
    String clientOrderId;
    String brokerOrderId;
    String tradePair;
    String side;
    String requestedQty;
    @Builder.Default Instant submittedAt = Instant.now();
    @Builder.Default int timeoutSeconds = 30;

    public enum Status { PENDING, CONFIRMED, TIMED_OUT, REJECTED }

    Status status;
    Instant confirmedAt;
    String errorReason;

    public boolean isTimedOut() {
        return submittedAt != null &&
                Instant.now().isAfter(submittedAt.plusSeconds(timeoutSeconds));
    }
}
