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
        Unsafe unsafe = unsafe();
        long offset = unsafe.getLong(field,0);
        unsafe.putObject(target, offset, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
