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
public class SystemCoreDependencies {
    private final Exchange exchange;
    private final TradingService tradingService;
    private final StrategyEngine strategyEngine;
    private final RiskManagementSystem riskManagementSystem;
    private final AiReasoningService aiReasoningService;
    private final ExecutionEngine executionEngine;
    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private final NotificationService notificationService;
    private final NewsDataProvider newsDataProvider;

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

    public SystemCoreDependencies(
            Exchange exchange,
            TradingService tradingService,
            StrategyEngine strategyEngine,
            RiskManagementSystem riskManagementSystem,
            AiReasoningService aiReasoningService,
            ExecutionEngine executionEngine,
            TradeExecutionCoordinator tradeExecutionCoordinator,
            NotificationService notificationService,
            NewsDataProvider newsDataProvider) {
        this.exchange = exchange;
        this.tradingService = tradingService;
        this.strategyEngine = strategyEngine;
        this.riskManagementSystem = riskManagementSystem;
        this.aiReasoningService = aiReasoningService;
        this.executionEngine = executionEngine;
        this.tradeExecutionCoordinator = tradeExecutionCoordinator;
        this.notificationService = notificationService;
        this.newsDataProvider = newsDataProvider;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public TradingService getTradingService() {
        return tradingService;
    }

    public StrategyEngine getStrategyEngine() {
        return strategyEngine;
    }

    public RiskManagementSystem getRiskManagementSystem() {
        return riskManagementSystem;
    }

    public AiReasoningService getAiReasoningService() {
        return aiReasoningService;
    }

    public ExecutionEngine getExecutionEngine() {
        return executionEngine;
    }

    public TradeExecutionCoordinator getTradeExecutionCoordinator() {
        return tradeExecutionCoordinator;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public NewsDataProvider getNewsDataProvider() {
        return newsDataProvider;
    }
}
