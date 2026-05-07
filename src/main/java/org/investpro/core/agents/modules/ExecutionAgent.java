package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.core.agents.risk.RiskReviewResult;
import org.investpro.enums.CapitalProtection;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.SystemDesign;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;

import java.util.Map;
import java.util.Objects;

/**
 * Agent responsible for trade execution coordination.
 * <p>
 * Processes:
 * - Risk-approved signals
 * - AI reasoning results
 * - Execution commands
 * <p>
 * Publishes:
 * - Execution events
 * - Order submissions
 * - Trade execution notifications
 * <p>
 * Note: This agent publishes execution requests but does not directly
 * place orders. Final execution goes through TradeExecutionCoordinator
 * and FinalRiskGate.
 */
@Slf4j
public class ExecutionAgent implements org.investpro.core.agents.Agent {
    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private volatile boolean running = false;
    private AgentContext context;

    public ExecutionAgent(TradeExecutionCoordinator tradeExecutionCoordinator) {
        this.tradeExecutionCoordinator = Objects.requireNonNull(
                tradeExecutionCoordinator,
                "tradeExecutionCoordinator cannot be null");
    }

    @Override
    public String name() {
        return "ExecutionAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("ExecutionAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("ExecutionAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("ExecutionAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            if (AgentEvent.RISK_REVIEWED.equals(event.type())) {
                coordinateExecution(event);
            }

        } catch (Exception e) {
            log.error("Error processing execution event", e);
        }
    }

    private void coordinateExecution(AgentEvent event) {
        if (!(event.payload() instanceof Map<?, ?> rawContext)) {
            log.debug("ExecutionAgent ignored RISK_REVIEWED event without context map");
            return;
        }

        if (isTrue(rawContext.get("halt_pipeline")) || isTrue(rawContext.get("risk_blocked"))) {
            log.info("ExecutionAgent blocked by risk review. reason={}", rawContext.get("risk_reason"));
            return;
        }

        Object signalValue = rawContext.get("signal");
        if (!(signalValue instanceof StrategySignal signal)) {
            log.warn("ExecutionAgent cannot execute without StrategySignal");
            return;
        }

        RiskReviewResult review = rawContext.get("trade_review") instanceof RiskReviewResult result ? result : null;
        TradeRiskContext riskContext = rawContext.get("risk_context") instanceof TradeRiskContext existing
                ? existing
                : buildRiskContext(signal);

        tradeExecutionCoordinator.processReviewedSignal(signal, riskContext, review)
                .thenAccept(result -> log.info("ExecutionAgent execution result: {}", result.message()))
                .exceptionally(exception -> {
                    log.error("ExecutionAgent failed to coordinate execution", exception);
                    return null;
                });
    }

    private TradeRiskContext buildRiskContext(StrategySignal signal) {
        TradePair pair = context == null ? null : context.getSelectedTradePair();
        if (pair == null) {
            pair = parsePair(signal.getSymbol());
        }
        double entryPrice = signal.getEntryPrice() > 0.0 ? signal.getEntryPrice() : 1.0;
        double stopLoss = signal.getStopLossPrice() > 0.0 ? signal.getStopLossPrice() : entryPrice * 0.99;
        double takeProfit = signal.getTakeProfitPrice() > 0.0 ? signal.getTakeProfitPrice() : entryPrice * 1.02;

        return TradeRiskContext.builder()
                .symbol(pair)
                .assetClass(pair == null ? "UNKNOWN" : String.valueOf(pair.getAssetClass()))
                .contractType(pair == null ? "UNKNOWN" : String.valueOf(pair.getContractType()))
                .broker(context == null || context.getExchange() == null ? "" : context.getExchange().getName())
                .accountEquity(1000.0)
                .availableCash(1000.0)
                .currentOpenRisk(0.0)
                .requestedPositionSize(1.0)
                .requestedLeverage(1.0)
                .entryPrice(entryPrice)
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .expectedWinRate(signal.getWinProbability() > 0.0 ? signal.getWinProbability() : signal.getConfidence())
                .expectedRewardRiskRatio(signal.getRiskRewardRatio() > 0.0 ? signal.getRiskRewardRatio() : 2.0)
                .riskProfile(RiskProfile.CONSERVATIVE)
                .marketBehavior(signal.getMarketBehavior() == null ? MarketBehavior.RANGING : signal.getMarketBehavior())
                .executionStrategy(ExecutionStrategy.MARKET_ORDER)
                .liquidityProfile(pair == null || pair.getLiquidityProfile() == null
                        ? LiquidityProfile.NORMAL
                        : pair.getLiquidityProfile())
                .psychologyProfile(PsychologyProfile.CAUTIOUS)
                .probabilityLevel(probabilityFromConfidence(signal.getConfidence()))
                .capitalProtection(CapitalProtection.STRICT_STOPS)
                .systemDesign(SystemDesign.TECHNICAL_ANALYSIS)
                .tradingSessionStatus(pair == null ? signal.getSessionStatus() : pair.getTradingSessionStatus())
                .tradingSessionNotes(pair == null || pair.getTradingSession() == null
                        ? signal.getSessionNotes()
                        : pair.getTradingSession().getNotes())
                .volatility(0.25)
                .build();
    }

    private TradePair parsePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String[] parts = symbol.replace('_', '/').replace('-', '/').split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to parse TradePair from {}", symbol, exception);
            return null;
        }
    }

    private ProbabilityLevel probabilityFromConfidence(double confidence) {
        if (confidence >= 0.90) {
            return ProbabilityLevel.VERY_HIGH;
        }
        if (confidence >= 0.70) {
            return ProbabilityLevel.HIGH;
        }
        if (confidence >= 0.50) {
            return ProbabilityLevel.MODERATE;
        }
        if (confidence >= 0.30) {
            return ProbabilityLevel.LOW;
        }
        return ProbabilityLevel.VERY_LOW;
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
