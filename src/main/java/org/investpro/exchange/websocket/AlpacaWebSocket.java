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

public class AlpacaWebSocket extends ExchangeWebSocketClient{

    protected final Map<TradePair, ExchangeStreamConsumer> liveTradeConsumers =
            Collections.synchronizedMap(new HashMap<>());

    public AlpacaWebSocket(@NotNull URI serverUri, @NotNull Draft draft) {
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

    @Override
    protected void onConnected() {

    }
}
