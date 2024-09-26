package org.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;


public abstract class ExchangeWebSocketClient extends WebSocketClient {
    private static final Map<String, String> httpHeaders = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<TradePair, LiveTradesConsumer> liveTradeConsumers = new ConcurrentHashMap<>();
    protected BooleanProperty connectionEstablished;
    protected CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);


    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) {
        super(clientUri, clientDraft);
        connectionEstablished = new SimpleBooleanProperty(false);
    }

    public ExchangeWebSocketClient(URI clientUri, Draft_6455 clientDraft, String apiKey, String apiSecret) {
        super(
                clientUri,
                clientDraft,
                httpHeaders,
                5000
        );
        connectionEstablished = new SimpleBooleanProperty(false);

    }

    public ExchangeWebSocketClient(TradePair tradePair, URI uri, Draft_6455 draft6455, String apiKey, Object o) {
        super(uri, draft6455, httpHeaders, 5000);
    }

    public abstract CountDownLatch getInitializationLatch();

    public void setInitializationLatch(CountDownLatch countDownLatch) {
        this.webSocketInitializedLatch = countDownLatch;
    }
    public abstract void stopStreamLiveTrades(TradePair tradePair);
    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    public abstract void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void onError(Exception exception);

    public abstract void onOpen(ServerHandshake handshake);

    public abstract boolean connectBlocking() throws InterruptedException;

    public Boolean isConnected() {
        return connectionEstablished.get();
    }
}
