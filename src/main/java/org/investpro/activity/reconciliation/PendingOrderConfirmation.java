package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class PendingOrderConfirmation {
    String exchangeId;
    String accountId;
    String orderId;
    String clientOrderId;
    BigDecimal expectedQuantity;
    BigDecimal expectedPrice;
    Instant submittedAt;
    int maxWaitSeconds;
}
