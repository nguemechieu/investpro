package org.investpro.exchange.websocket;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.TradePair;

import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import java.util.concurrent.CountDownLatch;

/**
 * An abstract {@code WebSocketClient} implementation that encapsulates common functionality
 * needed by {@code Exchange} implementations to interface with Websocket APIs.
 *

 */
public abstract class ExchangeWebSocketClient extends CoinbaseWebSocketClient {

    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);

    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);

    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft) {
        super(clientUri, clientDraft, "");
        connectionEstablished = new SimpleBooleanProperty(false);
    }

    protected ExchangeWebSocketClient(URI clientUri, Draft clientDraft, String jwt) {
        super(clientUri, clientDraft, jwt);
        connectionEstablished = new SimpleBooleanProperty(false);
    }

    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;
    }

    public abstract void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer);

    public abstract void stopStreamLiveTrades(TradePair tradePair);

    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    @Override
    public void onError(Exception exception) {
        logger.error("WebSocketClient error (%s): %s".formatted(getURI().getHost(), exception.getMessage()), exception);
        connectionEstablished.set(false);
        webSocketInitializedLatch.countDown();
    }

    @Override
    public boolean connectBlocking() throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            logger.error("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
            throw new RuntimeException("attempted to connect to an ExchangeWebSocketClient on the JavaFX thread!");
        }

        boolean result = super.connectBlocking();
        connectionEstablished.set(result);
        webSocketInitializedLatch.countDown();
        return result;
    }

    /**
     * Check if WebSocket is currently connected.
     * Safe to call from any thread - returns cached state if on FX thread,
     * actual state from property getter otherwise.
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        try {
            if (Platform.isFxApplicationThread()) {
                return connectionEstablished.get();
            }
            return super.isOpen();
        } catch (Exception e) {
            logger.warn("Error checking connection status: %s".formatted(e.getMessage()));
            return false;
        }
    }
}
