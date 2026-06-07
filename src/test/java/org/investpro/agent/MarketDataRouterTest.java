package org.investpro.agent;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.agent.symbol.SymbolAgent;
import org.investpro.agent.symbol.SymbolAgentMode;
import org.investpro.agent.symbol.SymbolAgentState;
import org.investpro.agent.symbol.SymbolAgentStatus;
import org.investpro.data.CandleData;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataRouterTest {

    @Test
    void routesMarketAndBrokerEventsToRegisteredSymbolAgent() throws Exception {
        MarketDataRouter router = new MarketDataRouter();
        RecordingAgent agent = new RecordingAgent("Coinbase", pair());

        router.registerAgent("Coinbase", agent);
        router.onCandle("Coinbase", pair(), candle());
        router.onTicker("Coinbase", pair(), BigDecimal.valueOf(45000));
        router.onOrderBook("Coinbase", pair(), "book");
        agent.onBrokerActivity(BrokerActivityEvent.builder()
                .eventId("event-1")
                .exchangeId("Coinbase")
                .tradePair(pair())
                .build());

        assertEquals(1, agent.candles.get());
        assertEquals(1, agent.tickers.get());
        assertEquals(1, agent.orderBooks.get());
        assertEquals(1, agent.brokerEvents.get());
    }

    @Test
    void ignoresUnregisteredSymbols() throws Exception {
        MarketDataRouter router = new MarketDataRouter();
        RecordingAgent agent = new RecordingAgent("Coinbase", pair());

        router.registerAgent("Coinbase", agent);
        router.onCandle("Binance", pair(), candle());

        assertEquals(0, agent.candles.get());
    }

    private static CandleData candle() {
        return new CandleData(1, 10, 10, 1, (int) (System.currentTimeMillis() / 1000), 1);
    }

    private static TradePair pair() throws SQLException, ClassNotFoundException {
        return new TradePair("BTC", "USDC");
    }

    private static final class RecordingAgent implements SymbolAgent {
        private final String exchangeId;
        private final TradePair tradePair;
        private final AtomicInteger candles = new AtomicInteger();
        private final AtomicInteger tickers = new AtomicInteger();
        private final AtomicInteger orderBooks = new AtomicInteger();
        private final AtomicInteger brokerEvents = new AtomicInteger();

        private RecordingAgent(String exchangeId, TradePair tradePair) {
            this.exchangeId = exchangeId;
            this.tradePair = tradePair;
        }

        @Override
        public TradePair pair() {
            return tradePair;
        }

        @Override
        public SymbolAgentState state() {
            return new SymbolAgentState(tradePair, exchangeId, SymbolAgentStatus.CREATED, SymbolAgentMode.PAPER,
                    null, null, null, null, null, null, 0, false, true, true, false,
                    false, "", "", java.util.Map.of());
        }

        @Override
        public void start() {
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void assignStrategy(StrategyDefinition strategyDefinition) {
        }

        @Override
        public void unassignStrategy() {
        }

        @Override
        public void onCandle(CandleData candle) {
            candles.incrementAndGet();
        }

        @Override
        public void onTicker(Object lastPrice) {
            tickers.incrementAndGet();
        }

        @Override
        public void onOrderBook(Object orderBook) {
            orderBooks.incrementAndGet();
        }

        @Override
        public void onBrokerActivity(BrokerActivityEvent event) {
            brokerEvents.incrementAndGet();
        }

        @Override
        public CompletableFuture<Void> evaluateNow(String trigger) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> reviewStrategyNow(String reason) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
