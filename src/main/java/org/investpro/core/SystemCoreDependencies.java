package org.investpro.core;

import org.investpro.ai.AiReasoningService;
import org.investpro.core.agents.execution.ExecutionEngine;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.exchange.Exchange;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.service.TradingService;
import org.investpro.strategy.StrategyEngine;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable dependency container for SystemCore services.
 * <p>
 * Used by AgentModules to access core application services
 * without tight coupling to SystemCore itself.
 */
public record SystemCoreDependencies(@NotNull Exchange exchange, @NotNull TradingService tradingService,
                                     @NotNull StrategyEngine strategyEngine,
                                     @NotNull RiskManagementSystem riskManagementSystem,
                                     @NotNull AiReasoningService aiReasoningService,
                                     @NotNull ExecutionEngine executionEngine,
                                     @NotNull TradeExecutionCoordinator tradeExecutionCoordinator) {

    public SystemCoreDependencies(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            @NotNull StrategyEngine strategyEngine,
            @NotNull RiskManagementSystem riskManagementSystem,
            @NotNull AiReasoningService aiReasoningService,
            @NotNull ExecutionEngine executionEngine,
            @NotNull TradeExecutionCoordinator tradeExecutionCoordinator) {
        this.exchange = Objects.requireNonNull(exchange, "exchange cannot be null");
        this.tradingService = Objects.requireNonNull(tradingService, "tradingService cannot be null");
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine cannot be null");
        this.riskManagementSystem = Objects.requireNonNull(riskManagementSystem, "riskManagementSystem cannot be null");
        this.aiReasoningService = Objects.requireNonNull(aiReasoningService, "aiReasoningService cannot be null");
        this.executionEngine = Objects.requireNonNull(executionEngine, "executionEngine cannot be null");
        this.tradeExecutionCoordinator = Objects.requireNonNull(tradeExecutionCoordinator,
                "tradeExecutionCoordinator cannot be null");
    }
}
