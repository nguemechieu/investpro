package org.investpro.core.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.TradeRiskContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * TradeRiskContextBuilder provides helper methods to construct TradeRiskContext
 * from different input sources (signals, manual trades, etc.).
 * <p>
 * NOTE: TradeRiskContext uses TradePair objects, not symbol strings.
 * These builders accept common symbol formats and normalize them into a
 * TradePair before the risk engine evaluates the request.
 */
@Slf4j
public class TradeRiskContextBuilder {

    private TradeRiskContextBuilder() {
        // Utility class
    }

    /**
     * Build TradeRiskContext from a manual trade UI request.
     * <p>
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

        String normalizedSymbol = normalizeSymbol(symbol);
        String[] parts = normalizedSymbol.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("symbol must identify a base and quote asset: " + symbol);
        }

        TradePair pair = parseTradePair(symbol);

        return TradeRiskContext.builder()
                .symbol(pair)
                .assetClass(inferAssetClass(normalizedSymbol))
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
     * <p>
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

        String normalizedSymbol = normalizeSymbol(symbol);
        String[] parts = normalizedSymbol.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("symbol must identify a base and quote asset: " + symbol);
        }

        TradePair pair = parseTradePair(symbol);

        return TradeRiskContext.builder()
                .symbol(pair)
                .assetClass(inferAssetClass(normalizedSymbol))
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

        String normalizedSymbol = normalizeSymbol(symbol);
        String upper = normalizedSymbol.toUpperCase();

        // Crypto detection
        if (upper.contains("BTC") || upper.contains("ETH") || upper.contains("USDT") ||
                upper.contains("USDC") || upper.contains("DOGE") || upper.contains("SHIB")) {
            return "CRYPTO";
        }

        // Forex detection (3-letter codes)
        if (upper.matches("[A-Z]{3}/[A-Z]{3}")) {
            return "FOREX";
        }

        // Standard forex pairs with 3-char base/quote
        String[] parts = normalizedSymbol.split("/");
        if (parts.length == 2 && parts[0].length() == 3 && parts[1].length() == 3) {
            return "FOREX";
        }

        return "UNKNOWN";
    }

    private static TradePair parseTradePair(@NotNull String symbol) {
        String normalized = normalizeSymbol(symbol);
        String[] parts = normalized.split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to parse TradePair from {}", symbol, exception);
            return null;
        }
    }

    private static String normalizeSymbol(@NotNull String symbol) {
        return symbol.trim().replace('_', '/').replace('-', '/');
    }
}
