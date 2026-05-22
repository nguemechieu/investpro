package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ReconciliationMismatch {
    String exchangeId;
    String accountId;
    MismatchType mismatchType;
    String entityId;
    String localValue;
    String brokerValue;
    String description;
    @Builder.Default Instant detectedAt = Instant.now();
    boolean repaired;
    String repairEventId;
}
