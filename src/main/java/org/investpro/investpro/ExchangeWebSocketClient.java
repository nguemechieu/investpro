package org.investpro.investpro;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.java_websocket.drafts.Draft;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;


public abstract class ExchangeWebSocketClient extends org.java_websocket.client.WebSocketClient {
    protected final BooleanProperty connectionEstablished;
    protected final Map<String, LiveTradesConsumer> liveTradeConsumers = new ConcurrentHashMap<String, LiveTradesConsumer>();
    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);


    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) {
        super(clientUri, clientDraft);
        connectionEstablished = new SimpleBooleanProperty(false);
    }

    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract void streamLiveTrades(String tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void stopStreamLiveTrades(String tradePair);


    public abstract boolean supportsStreamingTrades(String tradePair);

    @Override
    public void onError(Exception exception) {
        Log.error("WebSocketClient error (" + getURI().getHost() + "): " + exception);
        // FIXME: throw!
    }

    @Override
    public boolean connectBlocking() throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            Log.error("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
            throw new RuntimeException("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
        }

        boolean result = super.connectBlocking();
        connectionEstablished.set(result);
        webSocketInitializedLatch.countDown();
        return result;
    }

    public abstract CompletableFuture<WebSocket> sendText(CharSequence data, boolean last);

    public abstract CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last);

    public abstract CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    public abstract CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    public abstract CompletableFuture<WebSocket> sendClose(int statusCode, String reason);

    public abstract void request(long n);

    public abstract String getSubprotocol();

    public abstract boolean isOutputClosed();

    public abstract boolean isInputClosed();

    public abstract void abort();
}
