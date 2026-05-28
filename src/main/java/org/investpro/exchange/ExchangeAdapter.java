package org.investpro.exchange;

import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.ExchangeOperationResult;
import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Unified contract for all InvestPro exchange adapters.
 *
 * <p>Implementing this interface enables integration with the full institutional
 * exchange runtime: {@code ExchangeCapabilityRegistry}, {@code SmartExecutionRouter},
 * {@code ExchangeRuntimeManager}, and the {@code ExchangeAdapterMigrationBridge}.
 *
 * <h3>Design principles</h3>
 * <ul>
 *   <li><b>No null returns</b> — use {@link Optional} or {@link ExchangeOperationResult}.</li>
 *   <li><b>Async-first</b> — all I/O operations return {@link CompletableFuture}.</li>
 *   <li><b>Capability-aware</b> — optional endpoints have default no-op implementations;
 *       callers should check {@link ExchangeCapability} before invoking them.</li>
 *   <li><b>Incremental adoption</b> — legacy {@link Exchange} subclasses can implement
 *       this interface method-by-method; defaults prevent compilation failures.</li>
 * </ul>
 *
 * <h3>Migration path</h3>
 * <p>If an existing exchange already extends {@link Exchange}, use
 * {@link org.investpro.exchange.compat.ExchangeAdapterMigrationBridge} to wrap it without
 * changing the concrete class. Once ready, implement this interface directly and
 * remove the bridge wrapper.
 */
public interface ExchangeAdapter {

    // ── Identity ─────────────────────────────────────────────────────────────

    /**
     * Returns the unique exchange identifier used across the runtime
     * (e.g., {@code "coinbase"}, {@code "oanda"}, {@code "binance"}).
     *
     * @return lowercase exchange identifier
     */
    @NotNull String getExchangeId();

    /**
     * Returns the human-readable exchange name (e.g., {@code "Coinbase"}).
     *
     * @return display name
     */
    @NotNull String getDisplayName();

    /**
     * Returns the capability profile for this exchange, describing what
     * endpoints, asset types, order types, and streaming modes are supported.
     *
     * @return capability profile (never null)
     */
    @NotNull ExchangeCapability getCapability();

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Performs an authentication check against the exchange and returns a
     * structured result describing whether credentials are valid.
     *
     * <p>Must never throw — failures are encoded in {@link AuthCheckResult}.
     *
     * @return auth check result (always present)
     */
    @NotNull CompletableFuture<AuthCheckResult> checkAuth();

    // ── Market data ───────────────────────────────────────────────────────────

    /**
     * Fetches a normalized market snapshot for the given trade pair.
     *
     * <p>Returns {@link Optional#empty()} if the instrument is not available
     * or if the exchange does not support ticker data.
     *
     * @param pair the instrument to fetch
     * @return normalized snapshot or empty
     */
    default @NotNull CompletableFuture<Optional<NormalizedMarketSnapshot>> fetchSnapshot(
            @NotNull TradePair pair
    ) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Fetches the current order book for the given trade pair.
     *
     * <p>Returns {@link Optional#empty()} if the exchange does not support
     * order-book data or the instrument is not found.
     *
     * @param pair the instrument
     * @return order book or empty
     */
    default @NotNull CompletableFuture<Optional<OrderBook>> fetchOrderBook(
            @NotNull TradePair pair
    ) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Returns the list of tradeable instruments supported by this exchange.
     *
     * @return list of trade pairs (may be empty if unsupported)
     */
    default @NotNull CompletableFuture<List<TradePair>> fetchTradePairs() {
        return CompletableFuture.completedFuture(List.of());
    }

    // ── Account ───────────────────────────────────────────────────────────────

    /**
     * Fetches current account balances keyed by currency code.
     *
     * <p>Returns an empty result if auth has not been validated or the
     * exchange does not support balance queries.
     *
     * @return operation result containing balances map or error detail
     */
    default @NotNull CompletableFuture<ExchangeOperationResult<java.util.Map<String, BigDecimal>>>
    fetchBalances() {
        return CompletableFuture.completedFuture(
                ExchangeOperationResult.unsupported("fetchBalances", getExchangeId()));
    }

    /**
     * Fetches open positions.
     *
     * @return operation result containing positions or error detail
     */
    default @NotNull CompletableFuture<ExchangeOperationResult<List<String>>>
    fetchPositions() {
        return CompletableFuture.completedFuture(
                ExchangeOperationResult.unsupported("fetchPositions", getExchangeId()));
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    /**
     * Submits an order for execution.
     *
     * <p>Returns an unsupported result by default. Override this in adapters
     * that support live trading.
     *
     * @param request the normalized order request
     * @return execution result or error
     */
    default @NotNull CompletableFuture<ExchangeOperationResult<String>> submitOrder(
            @NotNull org.investpro.exchange.execution.ExecutionRequest request
    ) {
        return CompletableFuture.completedFuture(
                ExchangeOperationResult.unsupported("submitOrder", getExchangeId()));
    }

    /**
     * Cancels an order by its exchange-assigned identifier.
     *
     * @param orderId the exchange order ID
     * @return cancellation result
     */
    default @NotNull CompletableFuture<ExchangeOperationResult<Void>> cancelOrder(
            @NotNull String orderId
    ) {
        return CompletableFuture.completedFuture(
                ExchangeOperationResult.unsupported("cancelOrder", getExchangeId()));
    }

    // ── Connectivity ──────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the exchange is currently connected and
     * able to serve live market data.
     *
     * @return connectivity status
     */
    boolean isConnected();

    /**
     * Initiates a (re)connection to the exchange.
     * Implementations should be idempotent — calling while already connected
     * should be a no-op or a graceful reconnect.
     */
    default void connect() {}

    /**
     * Gracefully disconnects from the exchange, releasing all WebSocket
     * connections and background threads.
     */
    default void disconnect() {}
}
