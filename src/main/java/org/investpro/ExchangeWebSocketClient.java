package org.investpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.java_websocket.handshake.ClientHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
/**
 * An abstract {@code WebSocketClient} implementation that encapsulates common functionality
 * needed by {@code Exchange} implementations to interface with Websocket APIs.
 */
public abstract class ExchangeWebSocketClient  extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);

    protected final BooleanProperty connectionEstablished;
    protected final Map<TradePair, LiveTradesConsumer> liveTradeConsumers = new ConcurrentHashMap<>();
    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);


  WebSocketClient webSocketClient;
    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) {
        super(
                clientUri,
                clientDraft
        );
        webSocketClient= new WebSocketClient(clientUri, clientDraft) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {

                webSocketInitializedLatch.countDown();

            }

            @Override
            public void onMessage(String message) {
                try {
                    ExchangeWebSocketClient.this.onMessage(message);
                } catch (Exception e) {
                    logger.error("Error processing message", e);
                }

            }

            @Override
            public void onClose(int code, String reason, boolean remote) {

                logger.info("Connection closed: " + code + " " + reason);

            }

            @Override
            public void onError(Exception ex) {
                connectionEstablished.set(false);
                onClose(1000, ex.getMessage(), false);

            }
        };
        webSocketClient.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
        );
        webSocketClient.addHeader(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        );




webSocketClient.connect();








        connectionEstablished = new SimpleBooleanProperty(false);
        connectionEstablished.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                webSocketInitializedLatch.countDown();
            }
        });
    }

    public ExchangeWebSocketClient(URI clientUri, Draft_6455 clientDraft, String apiKey, String apiSecret) {
        this(clientUri, clientDraft);


    }


    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake);

    public abstract void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote);

    public abstract void onMessage(org.java_websocket.WebSocket conn, String message);

    public abstract void onError(org.java_websocket.WebSocket conn, Exception ex);

    public abstract void onStart();

    public abstract void onMessage(String message);

    public abstract void onClose(int code, String reason, boolean remote);

    public abstract void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void stopStreamLiveTrades(TradePair tradePair);

    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    public abstract boolean isStreamingTradesEnabled(TradePair tradePair);

    public abstract void sendText(CharSequence data, boolean last);



    public abstract void onError(Exception ex);

    public abstract boolean connectBlocking();

    @NotNull
    public abstract URI getURI();

    public abstract void onOpen(ServerHandshake serverHandshake);

    public abstract boolean isSupportsStreamingTrades();
}
