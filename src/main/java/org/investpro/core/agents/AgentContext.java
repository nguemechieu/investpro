package org.investpro.core.agents;

import lombok.Getter;
import lombok.Setter;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;

/**
 * Runtime context shared by all agents.
 */
@Getter
@Setter
public class AgentContext {


    private Exchange exchange;

    private TradingService tradingService;

    private AgentEventBus eventBus;

    private boolean autoTradingEnabled;

    private boolean aiReasoningEnabled;

    private double maxRiskPerTrade = 0.01;

    private double maxDailyLoss = 0.03;
    private String selectedSymbol = "";
    private TradePair selectedTradePair;

    public AgentContext() {
    }

    public AgentContext(Exchange exchange, TradingService tradingService, AgentEventBus eventBus) {
        this.exchange = exchange;
        this.tradingService = tradingService;
        this.eventBus = eventBus;
    }

    public void setSelectedSymbol(String selectedSymbol) {
        this.selectedSymbol = selectedSymbol == null ? "" : selectedSymbol;
    }

    public void setSelectedTradePair(TradePair selectedTradePair) {
        this.selectedTradePair = selectedTradePair;
        this.selectedSymbol = selectedTradePair == null ? "" : selectedTradePair.toString('/');
    }
}
