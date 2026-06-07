package org.investpro.agent;

import org.investpro.agent.symbol.SymbolAgent;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MarketDataRouter {

    private final ConcurrentMap<AgentKey, SymbolAgent> agents = new ConcurrentHashMap<>();

    public void registerAgent(String exchangeId, SymbolAgent agent) {
        if (agent == null) {
            return;
        }
        agents.put(AgentKey.of(exchangeId, agent.pair()), agent);
    }

    public void unregisterAgent(String exchangeId, SymbolAgent agent) {
        if (agent != null) {
            agents.remove(AgentKey.of(exchangeId, agent.pair()));
        }
    }

    public void onCandle(String exchangeId, TradePair pair, CandleData candle) {
        SymbolAgent agent = agents.get(AgentKey.of(exchangeId, pair));
        if (agent != null) {
            agent.onCandle(candle);
        }
    }

    public void onTicker(String exchangeId, TradePair pair, Object ticker) {
        SymbolAgent agent = agents.get(AgentKey.of(exchangeId, pair));
        if (agent != null) {
            agent.onTicker(ticker);
        }
    }

    public void onOrderBook(String exchangeId, TradePair pair, Object orderBook) {
        SymbolAgent agent = agents.get(AgentKey.of(exchangeId, pair));
        if (agent != null) {
            agent.onOrderBook(orderBook);
        }
    }
}
