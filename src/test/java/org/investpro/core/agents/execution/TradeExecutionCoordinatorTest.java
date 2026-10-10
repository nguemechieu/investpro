package org.investpro.core.agents.execution;

import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.investpro.core.SystemCore;
import org.investpro.decision.BotTradeDecisionEngine;
import org.investpro.decision.SignalToDecisionFilter;
import org.investpro.enums.CapitalProtection;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.SystemDesign;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeExecutionCoordinatorTest {
    @Test void queuedSignalFromPreviousSessionCannotExecuteAfterRestart() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = org.investpro.core.concurrent.AppExecutors.TRADING;
        for (int i = 0; i < 2; i++) org.investpro.core.concurrent.AppExecutors.submit(pool, () -> {
            started.countDown(); release.await(); return null;
        });
        var engine = org.mockito.Mockito.mock(ExecutionEngine.class);
        var ai = org.mockito.Mockito.mock(AiReasoningService.class);
        var coordinator = new TradeExecutionCoordinator(new RiskManagementSystem(), ai, engine);
        try {
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            var stale = coordinator.processSignal(Side.HOLD, TradeRiskContext.builder().build());
            coordinator.stopProcessing(); coordinator.resumeProcessing();
            release.countDown();
            assertTrue(stale.get(2, java.util.concurrent.TimeUnit.SECONDS).rejected());
            org.mockito.Mockito.verifyNoInteractions(engine, ai);
        } finally { release.countDown(); }
    }

    @Test
    void brokerOrderFailureBlocksAndReleasesLocksForNextSignal() {
        var exchange = org.mockito.Mockito.mock(org.investpro.exchange.Exchange.class);
        var provider = org.mockito.Mockito.mock(org.investpro.exchange.contracts.OrderExecutionProvider.class);
        var engine = org.mockito.Mockito.mock(ExecutionEngine.class);
        var ai = org.mockito.Mockito.mock(AiReasoningService.class);
        org.mockito.Mockito.when(engine.getExchange()).thenReturn(exchange);
        org.mockito.Mockito.when(exchange.getName()).thenReturn("failure-test");
        org.mockito.Mockito.when(exchange.botOrderExecution()).thenReturn(provider);
        org.mockito.Mockito.when(provider.fetchOpenOrders(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("broker offline")));
        var coordinator = new TradeExecutionCoordinator(new RiskManagementSystem(), ai, engine);
        var pair = org.mockito.Mockito.mock(TradePair.class);
        org.mockito.Mockito.when(pair.toString('/')).thenReturn("BTC/USD");
        var context = TradeRiskContext.builder().symbol(pair).build();
        var first = coordinator.processSignal(Side.BUY, context).join();
        var second = coordinator.processSignal(Side.BUY, context).join();
        assertTrue(first.rejected());
        assertTrue(second.message().contains("broker offline"));
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    @Test
    void brokerPositionFailureBlocksBeforeRiskAndAi() {
        var exchange = org.mockito.Mockito.mock(org.investpro.exchange.Exchange.class);
        var provider = org.mockito.Mockito.mock(org.investpro.exchange.contracts.OrderExecutionProvider.class);
        var engine = org.mockito.Mockito.mock(ExecutionEngine.class);
        var ai = org.mockito.Mockito.mock(AiReasoningService.class);
        org.mockito.Mockito.when(engine.getExchange()).thenReturn(exchange);
        org.mockito.Mockito.when(exchange.getName()).thenReturn("position-failure-test");
        org.mockito.Mockito.when(exchange.botOrderExecution()).thenReturn(provider);
        org.mockito.Mockito.when(provider.fetchOpenOrders(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of()));
        org.mockito.Mockito.when(exchange.fetchPosition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("position unavailable")));
        var coordinator = new TradeExecutionCoordinator(new RiskManagementSystem(), ai, engine);
        var pair = org.mockito.Mockito.mock(TradePair.class);
        org.mockito.Mockito.when(pair.toString('/')).thenReturn("BTC/USD");
        var result = coordinator.processSignal(Side.BUY, TradeRiskContext.builder().symbol(pair).build()).join();
        assertTrue(result.message().contains("position unavailable"));
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    @Test
    void processSignalBlocksWhenDecisionFilterRejectsAndSkipsExecutionEngine() throws Exception {
        RiskManagementSystem riskManagementSystem = new RiskManagementSystem();
        AiReasoningService aiReasoningService = new AiReasoningService() {
            @Override
            public AiTradeReviewResponse reviewTrade(AiTradeReviewRequest request) {
                return AiTradeReviewResponse.incompleteDataResponse("Not used in this test");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getServiceName() {
                return "test-ai";
            }
        };
        ExecutionEngine executionEngine = allocateWithoutConstructor(ExecutionEngine.class);

        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                riskManagementSystem,
                aiReasoningService,
                executionEngine);

        SignalToDecisionFilter filter = new SignalToDecisionFilter(
                new BotTradeDecisionEngine(null),
                coordinator);

        SystemCore systemCore = allocateWithoutConstructor(SystemCore.class);
        setField(systemCore, "signalToDecisionFilter", filter);
        coordinator.setSystemCore(systemCore);

        TradePair symbol = TradePair.fromSymbol("XLM/USD");

        TradeRiskContext riskContext = TradeRiskContext.builder()
                .symbol(symbol)
                .assetClass("CRYPTO")
                .contractType("SPOT")
                .broker("TEST")
                .accountEquity(10_000.0)
                .availableCash(10_000.0)
                .currentOpenRisk(0.0)
                .requestedPositionSize(1.0)
                .entryPrice(1.0)
                .stopLossPrice(0.9)
                .takeProfitPrice(1.2)
                .riskProfile(RiskProfile.CONSERVATIVE)
                .marketBehavior(MarketBehavior.RANGING)
                .executionStrategy(ExecutionStrategy.MARKET_ORDER)
                .liquidityProfile(LiquidityProfile.NORMAL)
                .psychologyProfile(PsychologyProfile.CAUTIOUS)
                .probabilityLevel(ProbabilityLevel.LOW)
                .capitalProtection(CapitalProtection.STRICT_STOPS)
                .systemDesign(SystemDesign.TECHNICAL_ANALYSIS)
                .tradingSessionStatus(TradingSessionStatus.OPEN)
                .build();

        StrategySignal signal = StrategySignal.builder()
                .symbol("XLM/USD")
                .side(Side.BUY)
                .confidence(0.10)
                .entryPrice(1.0)
                .build();

        TradeExecutionCoordinator.TradeExecutionResult result = coordinator.processSignal(signal, riskContext).join();

        assertTrue(result.rejected());
        assertTrue(result.message().contains("Signal blocked by decision filter"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Unsafe unsafe = unsafe();
        return (T) unsafe.allocateInstance(type);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
