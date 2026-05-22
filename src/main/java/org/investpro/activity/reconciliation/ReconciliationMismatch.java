package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Value;
import org.investpro.activity.BrokerActivityEvent;

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
