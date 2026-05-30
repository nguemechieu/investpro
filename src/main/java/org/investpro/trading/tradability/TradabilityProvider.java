package org.investpro.trading.tradability;

import org.investpro.models.trading.TradePair;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Exchange-agnostic tradability provider.
 */
public interface TradabilityProvider {

    CompletableFuture<ProductTradabilityStatus> getTradabilityStatus(String productId);

    CompletableFuture<List<ProductTradabilityStatus>> getAllTradableProducts();

    CompletableFuture<Boolean> isTradeable(String productId);

    CompletableFuture<Boolean> canPlaceMarketOrder(String productId);

    CompletableFuture<Boolean> canPlaceLimitOrder(String productId);

    default CompletableFuture<ProductTradabilityStatus> getTradabilityStatus(TradePair pair) {
        return getTradabilityStatus(productId(pair));
    }

    default CompletableFuture<Boolean> isTradeable(TradePair pair) {
        return isTradeable(productId(pair));
    }

    default CompletableFuture<Boolean> canPlaceMarketOrder(TradePair pair) {
        return canPlaceMarketOrder(productId(pair));
    }

    default CompletableFuture<Boolean> canPlaceLimitOrder(TradePair pair) {
        return canPlaceLimitOrder(productId(pair));
    }

    default String productId(TradePair pair) {
        return pair == null ? "" : pair.toString('-').toUpperCase();
    }
}