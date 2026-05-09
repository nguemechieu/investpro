package org.investpro.exchange.websocket;

import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.trading.TradePair;
import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class IBKWebSocketClient  extends ExchangeWebSocketClient {

    protected final Map<TradePair, ExchangeStreamConsumer> liveTradeConsumers =
            Collections.synchronizedMap(new HashMap<>());

    public IBKWebSocketClient(URI serverUri, Draft draft) {
        super(serverUri, draft);
    }

    @Override
    public void subscribeStream(@NotNull String streamName, @NotNull Consumer<String> handler) {

    }

    @Override
    public void unsubscribeStream(@NotNull String streamName) {

    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {

    }

    @Override
    public void stopStreamLiveTrades(@NotNull TradePair tradePair) {

    }

    @Override
    public boolean supportsStreamingTrades(@NotNull TradePair tradePair) {
        return false;
    }


    /**
     * Called by the neutral ExchangeWebSocketClient after socket opens.
     */
    @Override
    protected void onConnected() {
//        try {
//            sendSubscribe(null, HEARTBEATS_CHANNEL);
//        } catch (Exception exception) {
//            log.debug("Unable to subscribe Coinbase heartbeats", exception);
//        }
//
//        synchronized (liveTradeConsumers) {
//            for (TradePair pair : liveTradeConsumers.keySet()) {
//                try {
//                    sendSubscribe(pair, MARKET_TRADES_CHANNEL);
//                    pendingSubscriptions.remove(pair);
//                    log.info("Resubscribed Coinbase market trades for {}", pair);
//                } catch (Exception exception) {
//                    pendingSubscriptions.add(pair);
//                    log.warn("Unable to resubscribe Coinbase trades for {}", pair, exception);
//                }
//            }
//        }
//
//        synchronized (rawStreamHandlers) {
//            for (String streamKey : rawStreamHandlers.keySet()) {
//                CoinbaseWebSocketClient.CoinbaseStream stream = parseCoinbaseStream(streamKey);
//                if (stream == null) {
//                    continue;
//                }
//
//                try {
//                    sendSubscribe(stream.tradePair(), stream.channel());
//                    log.info("Resubscribed Coinbase raw stream {}", stream.key());
//                } catch (Exception exception) {
//                    log.warn("Unable to resubscribe Coinbase raw stream {}", stream.key(), exception);
//                }
//            }
//        }
    }
}
