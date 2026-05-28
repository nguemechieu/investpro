package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * WebSocket-first orchestrator that manages the lifecycle of exchange
 * WebSocket connections and governs when REST fallback is activated.
 *
 * <p>Architecture:
 * <ul>
 *   <li>WebSocket is always the preferred data channel.</li>
 *   <li>REST is used <em>only</em> for: snapshots at startup, reconciliation,
 *       recovery probes after reconnect, and explicit failover.</li>
 *   <li>REST polling is suspended when WebSocket delivers data for an endpoint.</li>
 *   <li>REST fallback is activated when WebSocket fails to reconnect within
 *       the {@code fallbackActivationDelay}.</li>
 * </ul>
 *
 * <p>This class integrates with {@link ExchangeConnectivityManager} to record
 * WebSocket health events, and with {@link AdaptivePollingEngine} to signal
 * activity changes.
 */
@Slf4j
public final class WebSocketOrchestrator {

    private static final Duration DEFAULT_FALLBACK_DELAY = Duration.ofSeconds(30);
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(10);

    private final String exchangeName;
    @Nullable
    private final AgentEventBus eventBus;
    @Nullable
    private final ExchangeConnectivityManager connectivityManager;
    @Nullable
    private final AdaptivePollingEngine pollingEngine;

    private final AtomicReference<ExchangeWebSocketClient> wsClient = new AtomicReference<>(null);
    private final AtomicBoolean wsActive = new AtomicBoolean(false);
    private final AtomicBoolean restFallbackActive = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicLong lastWsMessageAt = new AtomicLong(0);
    private final AtomicLong wsMessagesReceived = new AtomicLong(0);
    private final Set<String> activeSymbolSubscriptions = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService healthMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-orchestrator-health-" + exchangeName);
        t.setDaemon(true);
        return t;
    });

    private final Duration fallbackActivationDelay;

    public WebSocketOrchestrator(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeConnectivityManager connectivityManager,
            @Nullable AdaptivePollingEngine pollingEngine
    ) {
        this(exchangeName, eventBus, connectivityManager, pollingEngine, DEFAULT_FALLBACK_DELAY);
    }

    public WebSocketOrchestrator(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeConnectivityManager connectivityManager,
            @Nullable AdaptivePollingEngine pollingEngine,
            @NotNull Duration fallbackActivationDelay
    ) {
        this.exchangeName = exchangeName;
        this.eventBus = eventBus;
        this.connectivityManager = connectivityManager;
        this.pollingEngine = pollingEngine;
        this.fallbackActivationDelay = fallbackActivationDelay;

        healthMonitor.scheduleAtFixedRate(
                this::evaluateWebSocketHealth,
                HEALTH_CHECK_INTERVAL.toSeconds(),
                HEALTH_CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    /**
     * Registers a WebSocket client to be managed by this orchestrator.
     *
     * <p>The orchestrator monitors the client’s connection state and
     * activates REST fallback if the WebSocket cannot reconnect.
     *
     * @param client the WebSocket client to manage
     */
    public void register(@NotNull ExchangeWebSocketClient client) {
        wsClient.set(client);
        // Attach connection listener via property binding
        client.connectionEstablished.addListener((obs, wasConnected, isConnected) -> {
            if (isConnected) {
                onWebSocketConnected();
            } else {
                onWebSocketDisconnected();
            }
        });
    }

    /**
     * Records that a WebSocket message was received.
     * Called by WebSocket message handlers.
     */
    public void recordWebSocketMessage() {
        lastWsMessageAt.set(System.currentTimeMillis());
        wsMessagesReceived.incrementAndGet();
    }

    /**
     * Returns {@code true} if WebSocket is active and delivering messages.
     *
     * @return websocket active status
     */
    public boolean isWebSocketActive() {
        return wsActive.get();
    }

    /**
     * Returns {@code true} if REST fallback is currently serving as the
     * primary data channel.
     *
     * @return REST fallback status
     */
    public boolean isRestFallbackActive() {
        return restFallbackActive.get();
    }

    /**
     * Returns {@code true} if the given endpoint should use REST polling.
     *
     * <p>REST is always used for non-streaming endpoints (ACCOUNT, POSITIONS,
     * ORDER_HISTORY, etc.). For streaming endpoints (PRICING), REST is used
     * only when WebSocket is inactive.
     *
     * @param endpoint the endpoint to check
     * @return whether REST polling should be used
     */
    public boolean shouldUseRest(@NotNull EndpointType endpoint) {
        return switch (endpoint) {
            case PRICING -> !wsActive.get(); // REST only when WebSocket down
            default -> true;                  // All others always use REST
        };
    }

    /**
     * Subscribes to the WebSocket stream for the given symbol.
     *
     * @param symbol  instrument symbol
     * @param handler message handler
     * @return {@code true} if subscription was registered
     */
    public boolean subscribe(@NotNull String symbol, @NotNull Consumer<String> handler) {
        ExchangeWebSocketClient client = wsClient.get();
        if (client == null || !client.isConnected()) {
            log.debug("WebSocket not connected for {}; deferring subscription for {}", exchangeName, symbol);
            return false;
        }
        activeSymbolSubscriptions.add(symbol);
        client.subscribeStream(symbol, handler);
        return true;
    }

    /** Unsubscribes from the WebSocket stream for the given symbol. */
    public void unsubscribe(@NotNull String symbol) {
        activeSymbolSubscriptions.remove(symbol);
        ExchangeWebSocketClient client = wsClient.get();
        if (client != null) {
            client.unsubscribeStream(symbol);
        }
    }

    /** Returns the count of WebSocket messages received since startup. */
    public long getWsMessagesReceived() {
        return wsMessagesReceived.get();
    }

    /** Returns the number of reconnect attempts since last successful connection. */
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /** Shuts down the health monitor. */
    public void shutdown() {
        healthMonitor.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private void onWebSocketConnected() {
        wsActive.set(true);
        reconnectAttempts.set(0);
        restFallbackActive.set(false);
        lastWsMessageAt.set(System.currentTimeMillis());
        log.info("WebSocket active for {}", exchangeName);
        if (connectivityManager != null) connectivityManager.onWebSocketConnected();
        if (pollingEngine != null) pollingEngine.onStrategyLabActiveChanged(false); // WS active = not idle
        publishEvent(AgentEvent.EXCHANGE_CONNECTED);
    }

    private void onWebSocketDisconnected() {
        wsActive.set(false);
        reconnectAttempts.incrementAndGet();
        log.warn("WebSocket disconnected for {} (attempt #{})", exchangeName, reconnectAttempts.get());
        if (connectivityManager != null) connectivityManager.onWebSocketDisconnected();
        publishEvent(AgentEvent.EXCHANGE_DISCONNECTED);
    }

    private void evaluateWebSocketHealth() {
        try {
            if (!wsActive.get()) {
                long disconnectedDurationMs = System.currentTimeMillis() - lastWsMessageAt.get();
                if (disconnectedDurationMs > fallbackActivationDelay.toMillis() && !restFallbackActive.get()) {
                    activateRestFallback();
                }
                return;
            }

            // Check for silent WebSocket (connected but no messages)
            long msSinceLastMessage = System.currentTimeMillis() - lastWsMessageAt.get();
            ExchangeWebSocketClient client = wsClient.get();
            if (client != null && client.isConnected() && msSinceLastMessage > 60_000) {
                log.warn("{} WebSocket silent for {}s — possible stale connection",
                        exchangeName, msSinceLastMessage / 1000);
            }
        } catch (Exception e) {
            log.debug("WebSocket health check error for {}: {}", exchangeName, e.getMessage());
        }
    }

    private void activateRestFallback() {
        if (restFallbackActive.compareAndSet(false, true)) {
            log.warn("{} activating REST fallback after WebSocket failure ({}s)",
                    exchangeName, fallbackActivationDelay.toSeconds());
            publishEvent(AgentEvent.EXCHANGE_HEALTH_CHANGED);
        }
    }

    private void publishEvent(@NotNull String eventType) {
        if (eventBus == null) return;
        try {
            Map<String, Object> meta = Map.of(
                    "exchangeName", exchangeName,
                    "wsActive", wsActive.get(),
                    "restFallbackActive", restFallbackActive.get(),
                    "reconnectAttempts", reconnectAttempts.get()
            );
            eventBus.publishAsync(AgentEvent.of(eventType, "WebSocketOrchestrator", exchangeName, meta));
        } catch (Exception ignored) {
        }
    }
}
