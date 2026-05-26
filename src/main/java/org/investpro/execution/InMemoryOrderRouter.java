package org.investpro.execution;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.activity.BrokerActivityType;
import org.investpro.risk.RiskDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class InMemoryOrderRouter implements OrderRouter {
    private final BrokerActivityRepository repository;

    public InMemoryOrderRouter(BrokerActivityRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrderRouteResult route(OrderIntent intent, RiskDecision riskDecision, ExecutionGuardDecision guardDecision) {
        if (intent == null) {
            return new OrderRouteResult(false, "", "", "Order intent is required.", null);
        }
        if (riskDecision == null || !riskDecision.canProceed()) {
            return new OrderRouteResult(false, intent.clientOrderId(), "", "Risk decision did not approve routing.", null);
        }
        if (guardDecision == null || !guardDecision.allowed()) {
            return new OrderRouteResult(false, intent.clientOrderId(), "", "Execution guard blocked routing.", null);
        }

        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .exchangeId(intent.exchangeId())
                .activityType(BrokerActivityType.ORDER_SUBMITTED)
                .orderId(intent.clientOrderId())
                .side(null)
                .requestedQuantity(intent.quantity() == null ? BigDecimal.ZERO : intent.quantity())
                .price(intent.limitPrice())
                .eventTime(Instant.now())
                .receivedAt(Instant.now())
                .source("OrderRouter")
                .metadata(Map.of(
                        "clientOrderId", intent.clientOrderId(),
                        "sourceStrategy", intent.sourceStrategy(),
                        "executionMode", intent.executionMode().name(),
                        "important", "submitted-is-not-filled"))
                .build();
        if (repository != null) {
            repository.save(event);
        }
        return new OrderRouteResult(true, intent.clientOrderId(), "", "Order submitted event recorded; awaiting broker confirmation.", event);
    }
}
