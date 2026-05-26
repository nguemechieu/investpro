package org.investpro.pipeline;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.activity.BrokerActivityType;
import org.investpro.activity.InMemoryBrokerActivityRepository;
import org.investpro.decision.BotTradeDecision;
import org.investpro.decision.DecisionAction;
import org.investpro.execution.*;
import org.investpro.market.MarketContext;
import org.investpro.projection.InMemoryProjectionService;
import org.investpro.projection.ProjectionSnapshot;
import org.investpro.reconciliation.ReconciliationEngine;
import org.investpro.reconciliation.ReconciliationResult;
import org.investpro.reconciliation.ReconciliationStatus;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.RiskEngine;
import org.investpro.strategy.signals.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelinePhase1Test {

    @Test
    void weakSignalsResultInHold() {
        SignalDecision decision = new SignalAggregator().aggregate(List.of(
                signal("weak-buy", TradingAction.BUY, "0.25"),
                signal("weak-buy-2", TradingAction.BUY, "0.20")));

        assertEquals(TradingAction.HOLD, decision.finalAction());
        assertFalse(decision.actionable());
    }

    @Test
    void conflictingBuySellSignalsResultInHold() {
        SignalDecision decision = new SignalAggregator().aggregate(List.of(
                signal("buyer", TradingAction.BUY, "0.70"),
                signal("seller", TradingAction.SELL, "0.68")));

        assertEquals(TradingAction.HOLD, decision.finalAction());
        assertTrue(decision.reasons().stream().anyMatch(reason -> reason.contains("too close")));
    }

    @Test
    void riskRejectsMissingStopLossWhenRequired() {
        OrderIntent intent = intent(BigDecimal.ZERO, BigDecimal.valueOf(1));
        RiskDecision decision = new RiskEngine(true, BigDecimal.ONE)
                .evaluate(intent, approvedBotDecision(), freshContext(false));

        assertFalse(decision.canProceed());
        assertTrue(decision.getReasons().stream().anyMatch(reason -> reason.contains("Stop loss")));
    }

    @Test
    void executionGuardBlocksStaleData() {
        OrderIntent intent = intent(new BigDecimal("90"), BigDecimal.valueOf(1));
        RiskDecision riskDecision = RiskDecision.approved("ok", 1.0, 1.0);

        ExecutionGuardDecision guardDecision = new ExecutionGuard().check(intent, riskDecision, staleContext());

        assertFalse(guardDecision.allowed());
        assertEquals("STALE_MARKET_DATA", guardDecision.blockingCondition());
    }

    @Test
    void orderSubmissionDoesNotCreateFilledTrade() {
        BrokerActivityRepository repository = new InMemoryBrokerActivityRepository();
        OrderIntent intent = intent(new BigDecimal("90"), BigDecimal.valueOf(1));
        OrderRouteResult result = new InMemoryOrderRouter(repository)
                .route(intent, RiskDecision.approved("ok", 1.0, 1.0), ExecutionGuardDecision.allowed(List.of()));

        assertTrue(result.submitted());
        assertEquals(BrokerActivityType.ORDER_SUBMITTED, result.submittedEvent().getActivityType());
        assertEquals(0, repository.findByTimeRange("TEST", null, Instant.EPOCH, Instant.now()).stream()
                .filter(event -> event.getActivityType() == BrokerActivityType.ORDER_FILLED)
                .count());
    }

    @Test
    void repositoryStoresAndRetrievesBrokerEvents() {
        InMemoryBrokerActivityRepository repository = new InMemoryBrokerActivityRepository();
        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .eventId("evt-1")
                .exchangeId("TEST")
                .activityType(BrokerActivityType.ORDER_SUBMITTED)
                .orderId("order-1")
                .eventTime(Instant.now())
                .build();

        repository.save(event);

        assertTrue(repository.exists("TEST", "evt-1"));
        assertEquals(1, repository.findByOrderId("TEST", null, "order-1").size());
    }

    @Test
    void reconciliationDetectsMismatchedOrderPositionState() {
        InMemoryBrokerActivityRepository repository = new InMemoryBrokerActivityRepository();
        repository.save(BrokerActivityEvent.builder()
                .eventId("evt-1")
                .exchangeId("TEST")
                .activityType(BrokerActivityType.ORDER_SUBMITTED)
                .orderId("order-1")
                .eventTime(Instant.now())
                .build());
        ProjectionSnapshot projection = new InMemoryProjectionService()
                .rebuild(repository.findByTimeRange("TEST", null, Instant.EPOCH, Instant.now()));

        ReconciliationResult result = new ReconciliationEngine(repository)
                .reconcile(projection, new ReconciliationEngine.BrokerTruthSnapshot(0, 0, Instant.now()));

        assertEquals(ReconciliationStatus.MISMATCH_DETECTED, result.status());
        assertTrue(result.liveExecutionBlocked());
        assertTrue(repository.findByTimeRange("SYSTEM", null, Instant.EPOCH, Instant.now()).stream()
                .anyMatch(event -> event.getActivityType() == BrokerActivityType.RECONCILIATION_MISMATCH));
    }

    private StrategySignal signal(String name, TradingAction action, String confidence) {
        return new StrategySignal(name, "TEST", "EUR/USD", action, new BigDecimal(confidence), name,
                BigDecimal.ZERO, BigDecimal.ZERO, java.time.Duration.ZERO, null, Instant.now(), Map.of());
    }

    private OrderIntent intent(BigDecimal stopLoss, BigDecimal quantity) {
        return new OrderIntent("TEST", "EUR/USD", TradingAction.BUY, "MARKET", quantity,
                BigDecimal.valueOf(100), BigDecimal.ZERO, stopLoss, BigDecimal.valueOf(120),
                "client-1", "test", "bot-1", "risk-1", "test", ExecutionMode.PAPER, Instant.now(), Map.of());
    }

    private BotTradeDecision approvedBotDecision() {
        return new BotTradeDecision("bot-1", DecisionAction.TRADE, true, BigDecimal.ONE,
                "approved", null, List.of(), List.of(), Instant.now(), Map.of());
    }

    private MarketContext freshContext(boolean pendingOrder) {
        return new MarketContext("TEST", "EUR/USD", "1m", null, List.of(), null,
                new BigDecimal("0.001"), BigDecimal.ZERO, BigDecimal.ZERO,
                "NORMAL", "OPEN",
                new MarketContext.AccountSnapshot(BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000),
                        BigDecimal.valueOf(10_000), Instant.now()),
                pendingOrder ? List.of("order-1") : List.of(),
                List.of(),
                new MarketContext.ExchangeCapabilities(true, true, true, true, true, true, false),
                MarketContext.DataFreshnessStatus.FRESH,
                Instant.now(),
                Map.of());
    }

    private MarketContext staleContext() {
        return new MarketContext("TEST", "EUR/USD", "1m", null, List.of(), null,
                new BigDecimal("0.001"), BigDecimal.ZERO, BigDecimal.ZERO,
                "NORMAL", "OPEN",
                new MarketContext.AccountSnapshot(BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000),
                        BigDecimal.valueOf(10_000), Instant.now()),
                List.of(),
                List.of(),
                new MarketContext.ExchangeCapabilities(true, true, true, true, true, true, false),
                MarketContext.DataFreshnessStatus.STALE,
                Instant.now(),
                Map.of());
    }
}
