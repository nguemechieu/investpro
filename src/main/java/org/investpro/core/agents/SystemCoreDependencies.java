package org.investpro.core.agents;

import org.investpro.ai.AiReasoningService;
import org.investpro.exchange.Exchange;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.service.NewsDataProvider;
import org.investpro.service.NotificationService;
import org.investpro.service.TradingService;
import org.investpro.strategy.StrategyEngine;
import org.investpro.core.agents.execution.ExecutionEngine;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;

/**
 * Immutable container of dependencies available to agents and modules.
 * Created by SystemCore at startup and passed to AgentModule.configure().
 */
public record SystemCoreDependencies(Exchange exchange, TradingService tradingService, StrategyEngine strategyEngine,
                                     RiskManagementSystem riskManagementSystem, AiReasoningService aiReasoningService,
                                     ExecutionEngine executionEngine,
                                     TradeExecutionCoordinator tradeExecutionCoordinator,
                                     NotificationService notificationService, NewsDataProvider newsDataProvider) {
    public SystemCoreDependencies(
            Exchange exchange,
            TradingService tradingService,
            StrategyEngine strategyEngine,
            RiskManagementSystem riskManagementSystem,
            AiReasoningService aiReasoningService,
            ExecutionEngine executionEngine,
            TradeExecutionCoordinator tradeExecutionCoordinator) {
        this(exchange, tradingService, strategyEngine, riskManagementSystem, aiReasoningService, executionEngine,
                tradeExecutionCoordinator, null, null);
    }

}
