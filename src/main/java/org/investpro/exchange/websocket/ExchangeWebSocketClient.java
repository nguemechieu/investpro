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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstract {@code WebSocketClient} implementation that encapsulates common
 * functionality
 * needed by {@code Exchange} implementations to interface with Websocket APIs.
 *
 * 
 */
public abstract class ExchangeWebSocketClient extends CoinbaseWebSocketClient {

    protected final CountDownLatch webSocketInitializedLatch = new CountDownLatch(1);

    private static final Logger logger = LoggerFactory.getLogger(ExchangeWebSocketClient.class);

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;

    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "WebSocket-Reconnect-" + getURI().getHost());
        t.setDaemon(true);
        return t;
    });

    private volatile AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile boolean manualClose = false;

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

        // Auto-reconnect with exponential backoff on error
        if (!manualClose) {
            scheduleReconnect();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);

        // Attempt reconnection if it was a remote close (EOF, connection reset, etc)
        // and not a manual disconnect
        if (remote && !manualClose) {
            logger.warn("WebSocket closed remotely (code={}, reason={}). Scheduling reconnection.", code, reason);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        int attempts = reconnectAttempts.get();

        if (attempts >= MAX_RECONNECT_ATTEMPTS) {
            logger.error("WebSocket reconnection failed after {} attempts. Giving up.", MAX_RECONNECT_ATTEMPTS);
            return;
        }

        // Exponential backoff: 2s, 4s, 8s, 16s, 32s (capped at 60s)
        long delaySeconds = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempts),
                MAX_RECONNECT_DELAY_SECONDS);

        reconnectAttempts.incrementAndGet();

        logger.info("Scheduling WebSocket reconnection attempt {}/{} in {} seconds",
                attempts + 1, MAX_RECONNECT_ATTEMPTS, delaySeconds);

        reconnectExecutor.schedule(() -> {
            try {
                logger.info("Attempting WebSocket reconnection {}/{}", reconnectAttempts.get(), MAX_RECONNECT_ATTEMPTS);
                reconnect();
            } catch (Exception ex) {
                logger.warn("WebSocket reconnection attempt failed: {}", ex.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public void reconnect() {
        try {
            if (isOpen()) {
                try {
                    close();
                } catch (Exception ex) {
                    logger.debug("Error closing before reconnect: {}", ex.getMessage());
                }
            }

            // Reset manual close flag and attempt connection
            manualClose = false;
            if (connectBlocking()) {
                logger.info("WebSocket reconnected successfully");
                reconnectAttempts.set(0); // Reset attempts on successful reconnect
            }
        } catch (InterruptedException ex) {
            logger.warn("WebSocket reconnect interrupted", ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logger.warn("WebSocket reconnect failed: {}", ex.getMessage());
        }
    }

    public void disconnect() {
        manualClose = true;
        reconnectAttempts.set(0);
        try {
            if (isOpen()) {
                close();
            }
        } catch (Exception ex) {
            logger.debug("Error during disconnect: {}", ex.getMessage());
        }
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
     * 
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
