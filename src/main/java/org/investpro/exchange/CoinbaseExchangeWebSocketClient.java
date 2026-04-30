package org.investpro.exchange;

import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.TradePair;
import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Concrete implementation of ExchangeWebSocketClient for Coinbase Advanced Trade.
 * Bridges the Coinbase-specific websocket functionality with the Exchange abstraction.
 */
public class CoinbaseExchangeWebSocketClient extends ExchangeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseExchangeWebSocketClient.class);
    private static final String MARKET_TRADES_CHANNEL = "market_trades";

    public CoinbaseExchangeWebSocketClient(URI uri, Draft draft) {
        super(uri, draft);
    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        if (liveTradesConsumer == null) {
            logger.warn("Cannot stream live trades: tradePair or consumer is null");
            return;
        }
        
        // Store the consumer mapping
        liveTradeConsumers.put(tradePair, liveTradesConsumer);
        setTradePair(tradePair);
        
        // Send subscription
        try {
            sendSubscribe(tradePair, MARKET_TRADES_CHANNEL);
        } catch (Exception e) {
            logger.error("Failed to stream live trades for {}: {}", tradePair, e.getMessage(), e);
        }
    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }
        
        liveTradeConsumers.remove(tradePair);
        
        try {
            sendUnsubscribe(tradePair, MARKET_TRADES_CHANNEL);
        } catch (Exception e) {
            logger.error("Failed to stop stream live trades for {}: {}", tradePair, e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        // Coinbase supports streaming trades for any valid trade pair
        return tradePair != null && !tradePair.toString('/').isEmpty();
    }
}
