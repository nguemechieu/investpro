package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentModule;
import org.investpro.core.agents.AgentRegistry;
import org.investpro.core.agents.SystemCoreDependencies;
import org.investpro.core.agents.risk.RiskReviewResult;
import org.investpro.core.agents.risk.RiskReviewer;
import org.investpro.enums.CapitalProtection;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.SystemDesign;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Default trading agent module that registers the standard set of agents.
 * <p>
 * Registers:
 * - MarketDataAgent: collects market data
 * - SignalAgent: generates trading signals
 * - RiskAgent: evaluates risk
 * - PortfolioAgent: monitors portfolio
 * - PositionManagementAgent: manages position lifecycle
 * - ExecutionAgent: coordinates trade execution
 * - AuditAgent: logs and audits events
 * <p>
 * This module is a default implementation that can be replaced or
 * extended with custom agent modules.
 */
@Slf4j
public class DefaultTradingAgentModule implements AgentModule {
    @Override
    public @NotNull String moduleId() {
        return "DefaultTradingAgentModule";
    }

    @Override
    public void configure(
            @NotNull AgentRegistry registry,
            @NotNull SystemCoreDependencies dependencies) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(dependencies, "dependencies cannot be null");

        try {
            // Register agents in logical order
            registerAgent(registry, new MarketDataAgent());
            registerAgent(registry, new SignalAgent());
            registerAgent(registry, new org.investpro.core.agents.risk.RiskAgent(createRiskReviewer(dependencies)));
            registerAgent(registry, new PortfolioAgent());
            registerAgent(registry, new PositionManagementAgent());
            registerAgent(registry, new ExecutionAgent(dependencies.tradeExecutionCoordinator()));
            registerAgent(registry, new AuditAgent());

            log.info(
                    "DefaultTradingAgentModule configured. agents registered={}",
                    registry.size());

        } catch (Exception e) {
            log.error("Failed to configure DefaultTradingAgentModule", e);
            throw new RuntimeException("Failed to configure DefaultTradingAgentModule", e);
        }
    }

    private void registerAgent(@NotNull AgentRegistry registry, @NotNull Agent agent) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(agent, "agent cannot be null");

        try {
            registry.register(agent);
            log.debug("Agent registered: {}", agent.name());

        } catch (Exception e) {
            log.error("Failed to register agent: {}", agent.name(), e);
            throw e;
        }
    }

    private RiskReviewer createRiskReviewer(@NotNull SystemCoreDependencies dependencies) {
        return request -> CompletableFuture.supplyAsync(() -> {
            StrategySignal signal = request.getSignal();
            TradeRiskContext riskContext = buildRiskContext(dependencies, signal);
            RiskDecision decision = dependencies.riskManagementSystem().evaluateTrade(riskContext);

            if (!decision.canProceed()) {
                return RiskReviewResult.builder()
                        .approved(false)
                        .stage("RISK_ENGINE")
                        .reason(decision.getHumanReadableSummary())
                        .blockers(decision.getBlockers())
                        .warnings(decision.getWarnings())
                        .metadata(java.util.Map.of("riskDecision", decision))
                        .build();
            }

            return RiskReviewResult.builder()
                    .approved(true)
                    .stage("RISK_ENGINE")
                    .reason(decision.getHumanReadableSummary())
                    .amount(decision.getFinalPositionSize())
                    .price(signal.getEntryPrice())
                    .strategyName(signal.getStrategyName())
                    .timeframe(String.valueOf(signal.getTimeframe()))
                    .side(String.valueOf(signal.getSide()))
                    .executionStrategy(String.valueOf(decision.getRecommendedExecutionStrategy()))
                    .warnings(decision.getWarnings())
                    .metadata(java.util.Map.of("riskDecision", decision, "riskContext", riskContext))
                    .build();
        });
    }

    private TradeRiskContext buildRiskContext(
            @NotNull SystemCoreDependencies dependencies,
            @NotNull StrategySignal signal) {
        TradePair pair = resolveTradePair(dependencies, signal);
        double entryPrice = signal.getEntryPrice() > 0.0 ? signal.getEntryPrice() : 1.0;
        double stopLoss = signal.getStopLossPrice() > 0.0 ? signal.getStopLossPrice() : entryPrice * 0.99;
        double takeProfit = signal.getTakeProfitPrice() > 0.0 ? signal.getTakeProfitPrice() : entryPrice * 1.02;
        double requestedSize = Math.max(1.0, signal.getMetadata() == null
                ? 1.0
                : asDouble(signal.getMetadata().get("requested_units"), 1.0));

        return TradeRiskContext.builder()
                .symbol(pair)
                .assetClass(pair == null ? "UNKNOWN" : String.valueOf(pair.getAssetClass()))
                .contractType(pair == null ? "UNKNOWN" : String.valueOf(pair.getContractType()))
                .broker(dependencies.exchange() == null ? "" : dependencies.exchange().getName())
                .accountEquity(1000.0)
                .availableCash(1000.0)
                .currentOpenRisk(0.0)
                .requestedPositionSize(requestedSize)
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

    private TradePair resolveTradePair(SystemCoreDependencies dependencies, StrategySignal signal) {
        if (dependencies.exchange() != null) {
            try {
                List<TradePair> exchangePairs = new ArrayList<>(dependencies.exchange().getTradePairSymbol());
                UniversalTradabilityService tradabilityService = new UniversalTradabilityService(dependencies.exchange(), null);
                List<SymbolTradability> statuses = tradabilityService.getTradability(exchangePairs).get();
                List<TradePair> botTradablePairs = statuses.stream()
                        .filter(Objects::nonNull)
                        .filter(SymbolTradability::canBeUsedForBotTrading)
                        .map(SymbolTradability::tradePair)
                        .filter(Objects::nonNull)
                        .toList();

                for (TradePair pair : botTradablePairs) {
                    if (pair != null && signal.getSymbol() != null
                            && pair.toString('/').equalsIgnoreCase(signal.getSymbol())) {
                        return pair;
                    }
                }
            } catch (Exception exception) {
                log.debug("Unable to resolve signal pair from exchange", exception);
            }
        }

        String symbol = signal.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String normalized = symbol.replace('_', '/').replace('-', '/');
        String[] parts = normalized.split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to create TradePair for {}", symbol, exception);
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

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
