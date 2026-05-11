package org.investpro.core.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.risk.TradeRiskContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * TradeRiskContextBuilder provides helper methods to construct TradeRiskContext
 * from different input sources (signals, manual trades, etc.).
 *
 * NOTE: TradeRiskContext uses TradePair objects, not symbol strings.
 * These builders accept strings for convenience but full symbol resolution
 * would require access to a TradePair registry.
 *
 * For now, we pass symbol as null and leave that for the risk engine to
 * resolve.
 */
@Slf4j
public class TradeRiskContextBuilder {

    private TradeRiskContextBuilder() {
        // Utility class
    }

    /**
     * Build TradeRiskContext from a manual trade UI request.
     *
     * Used when user clicks BUY/SELL in TradingWindow.
     *
     * @param symbol   the trading pair (e.g., "BTC/USDT")
     * @param action   "BUY" or "SELL"
     * @param quantity the requested quantity
     * @param exchange the exchange being used
     * @param account  the user's account
     * @return complete TradeRiskContext ready for risk evaluation
     */
    public static TradeRiskContext fromManualTrade(
            @NotNull String symbol,
            @NotNull String action,
            double quantity,
            @NotNull Exchange exchange,
            @NotNull Account account) {

        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(account, "account must not be null");

        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative: " + quantity);
        }

        String[] parts = symbol.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("symbol must be in format 'BASE/QUOTE': " + symbol);
        }

        // Build context with available account state
        // Note: symbol is null - risk engine may need to resolve from string
        return TradeRiskContext.builder()
                .symbol(null) // TODO: Resolve symbol string to TradePair
                .assetClass(inferAssetClass(symbol))
                .contractType("SPOT")
                .broker(exchange.getName())
                .accountEquity(account.getEquity())
                .availableCash(account.getAvailableBalance())
                .requestedPositionSize(quantity)
                .requestedLeverage(1.0)
                .entryPrice(0.0)
                .stopLossPrice(0.0)
                .takeProfitPrice(0.0)
                .currentOpenRisk(0.0)
                .usedMargin(account.getMarginUsed())
                .freeMargin(account.getMarginAvailable())
                .accountBalance(account.getTotalBalance())
                .build();
    }

    /**
     * Build TradeRiskContext from market data and current account state.
     *
     * For use by automated agents evaluating a trade decision.
     *
     * @param symbol          the trading pair string (e.g., "BTC/USDT")
     * @param requestedSize   the requested position size
     * @param entryPrice      the entry price
     * @param stopLossPrice   the stop loss price (optional, 0 for none)
     * @param takeProfitPrice the take profit price (optional, 0 for none)
     * @param exchange        the exchange
     * @param account         the account
     * @return complete TradeRiskContext
     */
    public static TradeRiskContext fromSignal(
            @NotNull String symbol,
            double requestedSize,
            double entryPrice,
            Double stopLossPrice,
            Double takeProfitPrice,
            @NotNull Exchange exchange,
            @NotNull Account account) {

        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(account, "account must not be null");

        String[] parts = symbol.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("symbol must be in format 'BASE/QUOTE': " + symbol);
        }

        return TradeRiskContext.builder()
                .symbol(null) // TODO: Resolve symbol string to TradePair
                .assetClass(inferAssetClass(symbol))
                .contractType("SPOT")
                .broker(exchange.getName())
                .accountEquity(account.getEquity())
                .availableCash(account.getAvailableBalance())
                .requestedPositionSize(requestedSize)
                .requestedLeverage(1.0)
                .entryPrice(entryPrice)
                .stopLossPrice(stopLossPrice != null && stopLossPrice > 0 ? stopLossPrice : 0.0)
                .takeProfitPrice(takeProfitPrice != null && takeProfitPrice > 0 ? takeProfitPrice : 0.0)
                .currentOpenRisk(0.0)
                .usedMargin(account.getMarginUsed())
                .freeMargin(account.getMarginAvailable())
                .accountBalance(account.getTotalBalance())
                .build();
    }

    /**
     * Infer asset class from symbol string.
     *
     * @param symbol the trading pair
     * @return "CRYPTO", "FOREX", or "UNKNOWN"
     */
    private static String inferAssetClass(@NotNull String symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");

        String upper = symbol.toUpperCase();

        // Crypto detection
        if (upper.contains("BTC") || upper.contains("ETH") || upper.contains("USDT") ||
                upper.contains("USDC") || upper.contains("DOGE") || upper.contains("SHIB")) {
            return "CRYPTO";
        }

        // Forex detection (3-letter codes)
        if (upper.matches("[A-Z]{3}_[A-Z]{3}")) {
            return "FOREX";
        }

        // Standard forex pairs with 3-char base/quote
        String[] parts = symbol.split("/");
        if (parts.length == 2 && parts[0].length() == 3 && parts[1].length() == 3) {
            return "FOREX";
        }

        return "UNKNOWN";
    }
}
