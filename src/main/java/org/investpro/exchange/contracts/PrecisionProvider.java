package org.investpro.exchange.contracts;


import org.investpro.models.trading.TradePair;
import org.investpro.utils.MARKET_TYPES;

import java.util.concurrent.CompletableFuture;

public interface PrecisionProvider {

    CompletableFuture<Boolean> validateOrder(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    );

    double normalizeAmount(TradePair tradePair, double amount);

    double normalizePrice(TradePair tradePair, double price);

    double getMinOrderAmount(TradePair tradePair);

    double getMinOrderNotional(TradePair tradePair);

    double getMaxLeverage(TradePair tradePair);

    CompletableFuture<Double> fetchLeverage(TradePair tradePair);

    CompletableFuture<String> setLeverage(TradePair tradePair, double leverage);

    /**
     * Returns the number of decimal places used when displaying prices for the given pair.
     * Implementations should derive this from instrument metadata (e.g. OANDA {@code displayPrecision}).
     * The default returns 5, a safe fallback for most forex pairs.
     */
    default int getDisplayPrecision(TradePair tradePair) {
        return 5;
    }
}