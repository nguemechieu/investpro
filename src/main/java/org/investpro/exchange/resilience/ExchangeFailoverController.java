package org.investpro.exchange.resilience;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Contracts for exchange failover and alternate data source integration.
 *
 * <p>This package defines the interfaces and contracts only. Concrete multi-provider
 * failover implementations are deferred to future milestones. Current implementations
 * should register a no-op or logging stub so the system compiles and runs without a
 * secondary provider.
 *
 * <p>Architecture preparation for:
 * <ul>
 *   <li>Alternate pricing source (second broker or data vendor)</li>
 *   <li>Alternate execution source (backup broker)</li>
 *   <li>Fallback data provider (REST or cached feed)</li>
 *   <li>Backup WebSocket source (secondary streaming endpoint)</li>
 *   <li>Remote exchange workers (distributed execution nodes)</li>
 *   <li>Cloud execution (exchange microservices)</li>
 * </ul>
 */
public final class ExchangeFailoverController {

    private ExchangeFailoverController() {}

    // =========================================================================
    // Pricing failover
    // =========================================================================

    /**
     * Source of live price data that can serve as a fallback when the primary
     * exchange pricing endpoint fails.
     *
     * <p>Implementations must be thread-safe and non-blocking.
     */
    public interface AlternatePricingSource {

        /**
         * Returns the name identifier of this pricing source.
         *
         * @return source name (e.g., "AlpacaFallback", "MarketDataVendor")
         */
        @NotNull String sourceName();

        /**
         * Returns true if this source is currently available and can deliver prices.
         *
         * @return availability flag
         */
        boolean isAvailable();

        /**
         * Fetches the latest price for the given instrument symbol.
         *
         * @param symbol the instrument symbol (e.g., "EUR_USD")
         * @return a future resolving to the bid/ask price as a double array [bid, ask]
         */
        @NotNull CompletableFuture<double[]> fetchPrice(@NotNull String symbol);
    }

    // =========================================================================
    // Execution failover
    // =========================================================================

    /**
     * Alternative execution source that can route orders when the primary broker
     * execution endpoint is degraded or circuit-open.
     *
     * <p>Note: Implementors are responsible for regulatory compliance.
     * Best execution obligations may restrict automatic rerouting.
     */
    public interface AlternateExecutionSource {

        /** Returns the name identifier of this execution source. */
        @NotNull String sourceName();

        /** Returns true if this source can accept and execute orders. */
        boolean isAvailable();

        /**
         * Routes a normalized order request to this alternate source.
         *
         * @param orderRequest serialized order payload (exchange-specific format)
         * @return a future resolving to the order ID assigned by the alternate source
         */
        @NotNull CompletableFuture<String> routeOrder(@NotNull String orderRequest);
    }

    // =========================================================================
    // Data provider fallback
    // =========================================================================

    /**
     * Fallback data provider for historical or snapshot data when primary
     * exchange data endpoints are unavailable.
     *
     * <p>May serve cached data, archived database records, or a secondary data vendor.
     */
    public interface FallbackDataProvider {

        /** Returns the name identifier of this data provider. */
        @NotNull String providerName();

        /** Returns true if this provider can serve data. */
        boolean isAvailable();

        /**
         * Fetches a data snapshot for the given key (e.g., symbol + timeframe).
         *
         * @param dataKey the data key
         * @return a future resolving to the raw JSON string data
         */
        @NotNull CompletableFuture<String> fetchSnapshot(@NotNull String dataKey);
    }

    // =========================================================================
    // WebSocket fallback
    // =========================================================================

    /**
     * Backup WebSocket source that can provide streaming market data when
     * the primary WebSocket connection cannot be re-established.
     */
    public interface BackupWebSocketSource {

        /** Returns the name identifier of this WebSocket source. */
        @NotNull String sourceName();

        /** Returns true if this backup WebSocket source is reachable. */
        boolean isAvailable();

        /**
         * Establishes a backup WebSocket connection for the given symbol.
         *
         * @param symbol  the instrument to subscribe to
         * @param handler raw message handler
         * @return a future that completes when the backup stream is active
         */
        @NotNull CompletableFuture<Void> activateBackupStream(
                @NotNull String symbol,
                @NotNull java.util.function.Consumer<String> handler
        );
    }

    // =========================================================================
    // Distributed execution preparation
    // =========================================================================

    /**
     * Interface for a remote exchange worker node.
     * Prepared for distributed execution and exchange microservice architecture.
     *
     * <p>Remote workers can run in separate JVMs, containers, or cloud functions.
     * They receive normalized {@code NormalizedOrderRequest} payloads and return
     * execution results.
     */
    public interface RemoteExchangeWorker {

        /** Returns the worker node identifier. */
        @NotNull String nodeId();

        /** Returns the region or zone of this worker. */
        @NotNull String region();

        /** Returns true if this worker is healthy and accepting work. */
        boolean isHealthy();

        /**
         * Submits a normalized order to this remote worker.
         *
         * @param normalizedOrderJson JSON-serialized NormalizedOrderRequest
         * @return a future resolving to the execution result JSON
         */
        @NotNull CompletableFuture<String> submitOrder(@NotNull String normalizedOrderJson);
    }
}
