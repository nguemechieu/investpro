package org.investpro.exchange.websocket;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.trading.TradePair;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Neutral base WebSocket client for all exchange adapters.
 *
 * Do not put Binance, Coinbase, OANDA, or Alpaca-specific subscription logic
 * here.
 *
 * Responsibilities:
 * - Own WebSocket connection state.
 * - Own generic raw stream handlers.
 * - Provide neutral reconnect behavior.
 * - Let subclasses implement exchange-specific subscribe/unsubscribe payloads.
 */
@Getter

@Slf4j
public abstract class ExchangeWebSocketClient extends WebSocketClient {

    protected final Map<String, Consumer<String>> streamHandlers = new ConcurrentHashMap<>();

    public final SimpleBooleanProperty connectionEstablished = new SimpleBooleanProperty(false);

    private CountDownLatch initializationLatch = new CountDownLatch(1);

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;

    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(
                r,
                "WebSocket-Reconnect-" + safeHost(getURI()));
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private volatile boolean manualClose = false;
    private volatile boolean reconnectEnabled = true;

    protected ExchangeWebSocketClient(@NotNull URI serverUri, @NotNull Draft draft) {
        super(serverUri, draft);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        setConnectionEstablished(true);
        reconnectAttempts.set(0);
        manualClose = false;

        log.info("{} WebSocket opened: {}", getClass().getSimpleName(), getURI());

        try {
            onConnected();
            // Signal that WebSocket is initialized and ready
            initializationLatch.countDown();
        } catch (Exception exception) {
            log.warn(
                    "{} onConnected hook failed: {}",
                    getClass().getSimpleName(),
                    exception.getMessage(),
                    exception);
        }
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        try {
            onRawMessage(message);
        } catch (Exception exception) {
            log.warn(
                    "{} failed to process raw message: {}",
                    getClass().getSimpleName(),
                    exception.getMessage(),
                    exception);
        }

        dispatchRawMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        setConnectionEstablished(false);

        log.warn(
                "{} WebSocket closed. remote={} code={} reason={}",
                getClass().getSimpleName(),
                remote,
                code,
                reason);

        if (shouldNotReconnect(code, reason)) {
            log.error(
                    "{} WebSocket rejected request. Auto-reconnect disabled. code={} reason={}",
                    getClass().getSimpleName(),
                    code,
                    reason);
            manualClose = true;
            return;
        }

        if (remote && reconnectEnabled && !manualClose) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception exception) {
        setConnectionEstablished(false);

        log.error(
                "{} WebSocket error: {}",
                getClass().getSimpleName(),
                exception == null ? "Unknown error" : exception.getMessage(),
                exception);

        if (reconnectEnabled && !manualClose) {
            scheduleReconnect();
        }
    }

    public boolean isConnected() {
        return connectionEstablished.get() && isOpen();
    }

    public ReadOnlyBooleanProperty connectionEstablishedProperty() {
        return connectionEstablished;
    }

    public CountDownLatch getInitializationLatch() {
        return initializationLatch;
    }

    public void enableReconnect(boolean enabled) {
        this.reconnectEnabled = enabled;
    }

    public void disconnectSafely() {
        manualClose = true;
        reconnectAttempts.set(0);

        try {
            if (isOpen()) {
                close();
            }
        } catch (Exception exception) {
            log.debug(
                    "{} error during disconnect: {}",
                    getClass().getSimpleName(),
                    exception.getMessage());
        } finally {
            setConnectionEstablished(false);
        }
    }

    public void shutdown() {
        disconnectSafely();

        try {
            reconnectExecutor.shutdownNow();
        } catch (Exception exception) {
            log.debug(
                    "{} reconnect executor shutdown failed: {}",
                    getClass().getSimpleName(),
                    exception.getMessage());
        }

        streamHandlers.clear();
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempts.get();

        if (attempt >= MAX_RECONNECT_ATTEMPTS) {
            log.error(
                    "{} WebSocket reconnection failed after {} attempts. Giving up.",
                    getClass().getSimpleName(),
                    MAX_RECONNECT_ATTEMPTS);
            return;
        }

        long delaySeconds = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt),
                MAX_RECONNECT_DELAY_SECONDS);

        reconnectAttempts.incrementAndGet();

        log.info(
                "{} WebSocket reconnection attempt {}/{} in {} seconds",
                getClass().getSimpleName(),
                attempt + 1,
                MAX_RECONNECT_ATTEMPTS,
                delaySeconds);

        reconnectExecutor.schedule(() -> {
            try {
                if (manualClose || !reconnectEnabled) {
                    return;
                }

                log.info(
                        "{} attempting WebSocket reconnection {}/{}",
                        getClass().getSimpleName(),
                        reconnectAttempts.get(),
                        MAX_RECONNECT_ATTEMPTS);

                reconnectBlocking();

            } catch (Exception exception) {
                log.warn(
                        "{} WebSocket reconnect failed: {}",
                        getClass().getSimpleName(),
                        exception.getMessage(),
                        exception);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public boolean reconnectBlocking() throws InterruptedException {
        try {
            if (isOpen()) {
                closeBlocking();
            }
        } catch (Exception exception) {
            log.debug(
                    "{} close before reconnect failed: {}",
                    getClass().getSimpleName(),
                    exception.getMessage());
        }

        manualClose = false;

        boolean connected = connectBlocking();

        if (connected) {
            setConnectionEstablished(true);
            reconnectAttempts.set(0);
            log.info("{} WebSocket reconnected successfully", getClass().getSimpleName());
        } else {
            setConnectionEstablished(false);
            log.warn("{} WebSocket reconnect returned false", getClass().getSimpleName());
        }
        return connected;
    }

    protected void dispatchRawMessage(String message) {
        for (Map.Entry<String, Consumer<String>> entry : streamHandlers.entrySet()) {
            try {
                entry.getValue().accept(message);
            } catch (Exception exception) {
                log.warn(
                        "{} stream handler failed. stream={} error={}",
                        getClass().getSimpleName(),
                        entry.getKey(),
                        exception.getMessage(),
                        exception);
            }
        }
    }

    protected void registerHandler(@NotNull String streamName, @NotNull Consumer<String> handler) {
        streamHandlers.put(normalizeStreamName(streamName), handler);
    }

    protected void removeHandler(@NotNull String streamName) {
        streamHandlers.remove(normalizeStreamName(streamName));
    }

    protected boolean hasHandler(@NotNull String streamName) {
        return streamHandlers.containsKey(normalizeStreamName(streamName));
    }

    protected String normalizeStreamName(@NotNull String streamName) {
        return streamName.trim().toLowerCase(Locale.ROOT);
    }

    private void setConnectionEstablished(boolean value) {
        try {
            if (Platform.isFxApplicationThread()) {
                connectionEstablished.set(value);
            } else {
                Platform.runLater(() -> connectionEstablished.set(value));
            }
        } catch (IllegalStateException exception) {
            // JavaFX toolkit may not be initialized during tests/headless mode.
            connectionEstablished.set(value);
        }
    }

    private boolean shouldNotReconnect(int code, @Nullable String reason) {
        if (code == 1008) {
            return true;
        }

        if (reason == null) {
            return false;
        }

        String lowerReason = reason.toLowerCase(Locale.ROOT);

        return lowerReason.contains("invalid request")
                || lowerReason.contains("unauthorized")
                || lowerReason.contains("invalid signature")
                || lowerReason.contains("bad request");
    }

    private static String safeHost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "unknown";
        }

        return uri.getHost();
    }

    public abstract void subscribeStream(
            @NotNull String streamName,
            @NotNull Consumer<String> handler);

    public abstract void unsubscribeStream(@NotNull String streamName);

    public abstract void streamLiveTrades(
            @NotNull TradePair tradePair,
            @NotNull ExchangeStreamConsumer consumer);

    public abstract void stopStreamLiveTrades(@NotNull TradePair tradePair);

    public abstract boolean supportsStreamingTrades(@NotNull TradePair tradePair);

    /**
     * Called after the socket opens.
     *
     * Subclasses should resubscribe active streams here.
     */
    protected abstract void onConnected();

    /**
     * Called for every incoming raw websocket message before generic dispatch.
     *
     * Subclasses can parse exchange-specific JSON here.
     */
    protected void onRawMessage(@NotNull String message) {
        // Optional subclass hook.
    }


    private void setInitializationLatch() {
        this.initializationLatch = new CountDownLatch(1);
        initializationLatch.countDown();
    }
}