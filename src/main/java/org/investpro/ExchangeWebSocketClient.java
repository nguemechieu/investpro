package org.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * An abstract {@code WebSocketClient} implementation that encapsulates common functionality
 * needed by {@code Exchange} implementations to interface with Websocket APIs.
 */
public abstract class ExchangeWebSocketClient extends WebSocketClient {

    protected final BooleanProperty connectionEstablished;
    protected final Map<TradePair, LiveTradesConsumer> liveTradeConsumers = new ConcurrentHashMap<>();
    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);
    private final Draft clientDraft;
    private final URI clientUri;


    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) {
        super(
                clientUri,
                clientDraft
        );
        this.clientUri = clientUri;
        this.clientDraft = clientDraft;

        connectionEstablished = new SimpleBooleanProperty(false);
        connectionEstablished.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                webSocketInitializedLatch.countDown();
            }
        });
    }

    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void stopStreamLiveTrades(TradePair tradePair);

    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    public abstract boolean isStreamingTradesEnabled(TradePair tradePair);

    public abstract CompletableFuture<WebSocket> sendText(CharSequence data, boolean last);

    public abstract CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last);


    public Draft getClientDraft() {
        return clientDraft;
    }

    public URI getClientUri() {
        return clientUri;
    }
}
