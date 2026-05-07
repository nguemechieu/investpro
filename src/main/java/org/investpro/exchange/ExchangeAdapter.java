package org.investpro.exchange;

import org.investpro.exchange.models.*;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Standard contract for all exchange adapters.
 *
 * <p>
 * All adapters (Coinbase, OANDA, Binance, etc.) must implement this interface
 * to ensure
 * consistent behavior and allow the UI and trading services to safely interact
 * with any exchange.
 *
 * <p>
 * <strong>Key Design Principles:</strong>
 * <ul>
 * <li>Return {@link Optional} or explicit result objects; never null</li>
 * <li>Auth logic stays in the adapter; UI never signs requests or builds
 * URLs</li>
 * <li>Adapters declare their capabilities via {@link #getCapability()}</li>
 * <li>Diagnostics are available via {@link #checkAuthentication()}</li>
 * </ul>
 */
public interface ExchangeAdapter {

    // ==================== Identity & Capabilities ====================

    /**
     * Get the display name of this exchange (e.g., "Coinbase", "OANDA").
     */
    @NotNull
    String getExchangeName();

    /**
     * Get this exchange's full capability profile.
     *
     * <p>
     * The UI uses this to determine what features to display and what to
     * disable/hide.
     */
    @NotNull
    ExchangeCapability getCapability();

    /**
     * Check if this exchange supports a specific feature.
     *
     * <p>
     * Convenience method; delegates to {@code getCapability().supports(feature)}.
     */
    default boolean supports(@NotNull ExchangeFeature feature) {
        return getCapability().supports(feature);
    }

    // ==================== Authentication & Diagnostics ====================

    /**
     * Test authentication and return diagnostic info.
     *
     * <p>
     * Should make a lightweight API call to verify credentials (e.g., GET
     * /accounts).
     * On failure, distinguish between credential issues and network/endpoint
     * failures.
     *
     * @return Auth check result with diagnostics
     */
    @NotNull
    AuthCheckResult checkAuthentication();

    // ==================== Instrument & Data Queries ====================

    /**
     * Get available trading pairs/instruments.
     *
     * <p>
     * Returns an empty list if unavailable; never null.
     */
    @NotNull
    List<TradePair> getInstruments();

    /**
     * Get the latest price for a trading pair.
     *
     * @return Ticker wrapped in Optional; empty if unavailable
     */
    @NotNull
    Optional<org.investpro.models.trading.Ticker> getLatestPrice(@NotNull TradePair pair);

    /**
     * Get the order book (depth) for a trading pair.
     *
     * <p>
     * Type of depth depends on {@link #getCapability()}.marketDepthType:
     * <ul>
     * <li>FULL_ORDER_BOOK: All bid/ask orders</li>
     * <li>TOP_OF_BOOK: Only best bid/ask</li>
     * <li>DISTRIBUTION_BOOK: Aggregated ranges (e.g., OANDA)</li>
     * </ul>
     *
     * @return OrderBook wrapped in Optional; empty if unavailable
     */
    @NotNull
    Optional<OrderBook> getOrderBook(@NotNull TradePair pair);

    /**
     * Get account/portfolio snapshot (balance, margin, positions, etc.).
     *
     * @return Account snapshot wrapped in Optional; empty if unavailable
     */
    @NotNull
    Optional<? extends Object> getAccountSnapshot();

    // ==================== Order Validation & Placement ====================

    /**
     * Validate an order before placement.
     *
     * <p>
     * Checks for invalid quantities, unsupported order types, instruments, etc.
     * Does not place the order; purely for validation.
     *
     * @param orderRequest The order to validate
     * @return Validation result
     */
    @NotNull
    Optional<OrderValidationResult> validateOrder(@NotNull Object orderRequest);

    /**
     * Place an order on the exchange.
     *
     * <p>
     * Should only be called after:
     * <ul>
     * <li>Authentication verified</li>
     * <li>Order validated</li>
     * <li>RiskEngine approved</li>
     * </ul>
     *
     * @param orderRequest The order to place
     * @return Result with order ID (on success) or error details (on failure)
     */
    @NotNull
    Optional<OrderResult> placeOrder(@NotNull Object orderRequest);

    // ==================== Streaming & Advanced ====================

    /**
     * Check if this adapter supports WebSocket or streaming.
     */
    boolean supportsWebSocketStreaming();

    /**
     * Check if this adapter supports streaming order book updates.
     */
    boolean supportsOrderBookStreaming();

    /**
     * Get the API base URL (for logging/diagnostics only).
     */
    @NotNull
    String getApiBaseUrl();
}
