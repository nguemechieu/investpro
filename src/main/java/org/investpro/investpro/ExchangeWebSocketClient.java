package org.investpro.investpro;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public abstract class ExchangeWebSocketClient implements WebSocket {

    protected final BooleanProperty connectionEstablished;
    protected final Map<TradePair, LiveTradesConsumer> liveTradeConsumers = new ConcurrentHashMap<>();
    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);

    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);

    protected ExchangeWebSocketClient(URI clientUri) {

        super();
        urlData= String.valueOf(clientUri);
        connectionEstablished = new SimpleBooleanProperty(false);
    }

    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void stopStreamLiveTrades(TradePair tradePair);

    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    public boolean connectBlocking() throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            logger.error("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
            throw new RuntimeException("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
        }

        boolean result =false;
        connectionEstablished.set(result);
        webSocketInitializedLatch.countDown();
        return result;
    }


    static String urlData;
    public URI getURI() throws URISyntaxException {
        URI uri = new URI(urlData);
        return uri;

    }
}
