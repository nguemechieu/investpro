package org.investpro.execution;

import org.investpro.activity.BrokerActivityEvent;

public record OrderRouteResult(
        boolean submitted,
        String clientOrderId,
        String brokerOrderId,
        String reason,
        BrokerActivityEvent submittedEvent) {
}
