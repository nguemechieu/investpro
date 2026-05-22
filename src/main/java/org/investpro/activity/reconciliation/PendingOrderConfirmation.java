package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class PendingOrderConfirmation {
    String exchangeId;
    String accountId;
    String orderId;
    String localStatus;
    @Builder.Default Instant submittedAt = Instant.now();
    int retryCount;
    String lastError;
}
