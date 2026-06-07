package org.investpro.agent.symbol;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyDefinition;

import java.util.concurrent.CompletableFuture;

public interface SymbolAgent {
    TradePair pair();

    SymbolAgentState state();

    void start();

    void pause();

    void resume();

    void stop();

    void assignStrategy(StrategyDefinition strategy);

    void unassignStrategy();

    void onCandle(CandleData candle);

    void onTicker(Object ticker);

    void onOrderBook(Object orderBook);

    void onBrokerActivity(BrokerActivityEvent event);

    CompletableFuture<Void> evaluateNow(String reason);

    CompletableFuture<Void> reviewStrategyNow(String reason);
}
