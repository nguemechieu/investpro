package org.investpro.market;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Adapter pattern: bridges exchange adapter output → MarketDataEngine input.
 * 
 * Exchange adapters (Binance, OANDA, Alpaca, etc.) implement
 * MarketDataProvider.
 * This adapter takes their output (Tickers, OrderBooks, CandleData) and feeds
 * it
 * into the central MarketDataEngine.
 * 
 * This decouples exchange-specific logic from market data caching/state
 * management.
 */
@Slf4j
public class ExchangeMarketDataAdapter {

    private final MarketDataEngine engine;
    private final String brokerName;

    public ExchangeMarketDataAdapter(
            @NotNull MarketDataEngine engine,
            @NotNull String brokerName) {
        this.engine = engine;
        this.brokerName = brokerName;
        log.debug("Created ExchangeMarketDataAdapter for broker: {}", brokerName);
    }

    /**
     * Consume a Ticker from exchange and update the cache.
     * Called when exchange adapter fetches live price for a pair.
     */
    public void consumeTicker(@NotNull Ticker ticker) {
        if (ticker == null || ticker.getTradePair() == null) {
            log.warn("[{}] Received null ticker", brokerName);
            return;
        }

        TradePair pair = ticker.getTradePair();
        engine.updateQuote(
                pair,
                ticker.getBidPrice(),
                ticker.getAskPrice(),
                ticker.getLastPrice(),
                ticker.getVolume());
        log.trace("[{}] Consumed ticker for {}", brokerName, pair);
    }

    /**
     * Consume multiple tickers from exchange and update the cache.
     */
    public void consumeTickers(@NotNull List<Ticker> tickers) {
        for (Ticker ticker : tickers) {
            consumeTicker(ticker);
        }
        log.debug("[{}] Consumed {} tickers", brokerName, tickers.size());
    }

    /**
     * Consume an OrderBook from exchange and update the cache.
     */
    public void consumeOrderBook(
            @NotNull TradePair tradePair,
            @NotNull OrderBook orderBook) {
        engine.updateOrderBook(tradePair, orderBook);
        log.trace("[{}] Consumed order book for {}", brokerName, tradePair);
    }

    /**
     * Consume candles for a pair and timeframe.
     */
    public void consumeCandles(
            @NotNull TradePair tradePair,
            @NotNull Timeframe timeframe,
            @NotNull List<CandleData> candles) {
        if (candles.isEmpty()) {
            log.trace("[{}] Skipping empty candle list for {}", brokerName, tradePair);
            return;
        }

        engine.updateCandles(tradePair, timeframe, candles);
        log.trace("[{}] Consumed {} candles for {} on {}",
                brokerName, candles.size(), tradePair, timeframe);
    }

    /**
     * Get the broker name this adapter represents.
     */
    public String getBrokerName() {
        return brokerName;
    }

    /**
     * Get the underlying engine (for advanced operations).
     */
    public MarketDataEngine getEngine() {
        return engine;
    }
}
