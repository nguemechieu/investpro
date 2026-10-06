package org.investpro.activities.reconciliation;

import lombok.Builder;
import lombok.Value;
import org.investpro.activities.BrokerActivityEvent;

@Value
@Builder
public class ReconciliationMismatch {
    MismatchType type;
    String exchangeId;
    String eventId;
    String orderId;
    String tradeId;
    String detail;
    BrokerActivityEvent localEvent;
    BrokerActivityEvent brokerEvent;
}
