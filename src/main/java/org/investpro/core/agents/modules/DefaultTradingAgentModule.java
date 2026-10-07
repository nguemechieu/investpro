package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentModule;
import org.investpro.core.agents.AgentRegistry;
import org.investpro.core.agents.SystemCoreDependencies;
import org.investpro.core.agents.risk.RiskReviewResult;
import org.investpro.core.agents.risk.RiskReviewer;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.jetbrains.annotations.NotNull;

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
        return org.investpro.core.pipeline.BotRiskContextService.fromSignal(
                dependencies.exchange(), resolveTradePair(dependencies, signal), signal);
    }

    private TradePair resolveTradePair(SystemCoreDependencies dependencies, StrategySignal signal) {
        if (dependencies.exchange() == null) throw new IllegalStateException("Execution exchange is unavailable");
        try {
            return dependencies.exchange().getTradePairSymbol().stream()
                    .filter(pair -> pair.toString('/').equalsIgnoreCase(signal.getSymbol()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Signal instrument is not in the exchange catalog"));
        } catch (java.sql.SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Exchange instrument catalog is unavailable", exception);
        }
    }
}
