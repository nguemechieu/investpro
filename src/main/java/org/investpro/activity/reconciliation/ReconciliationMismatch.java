package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReconciliationMismatch {
    String exchangeId;
    String eventId;
    MismatchType type;
    String localValue;
    String brokerValue;
    String description;
}
