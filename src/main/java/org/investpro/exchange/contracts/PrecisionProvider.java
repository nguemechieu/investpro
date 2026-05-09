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
}