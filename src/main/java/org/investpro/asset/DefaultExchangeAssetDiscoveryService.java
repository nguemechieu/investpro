package org.investpro.asset;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.ExchangeInstrumentService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
public final class DefaultExchangeAssetDiscoveryService implements ExchangeAssetDiscoveryService {
    private final Executor executor;

    public DefaultExchangeAssetDiscoveryService(Executor executor) {
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<AssetCatalogEntry>> discover(Exchange exchange, ExchangeId exchangeId) {
        if (exchange == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (exchangeId == ExchangeId.IBKR) {
            log.info("asset.catalog.discovery.skipped exchange={} reason=search-based-cache-only", exchangeId.id());
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> fetchPairs(exchange), executor)
                .thenApply(pairs -> pairs.stream()
                        .filter(pair -> pair != null)
                        .map(pair -> AssetCatalogEntry.fromTradePair(exchangeId, pair, Instant.now()))
                        .toList());
    }

    private List<TradePair> fetchPairs(Exchange exchange) {
        try {
            ExchangeInstrumentService instrumentService = exchange.instrumentService();
            if (instrumentService != null) {
                return instrumentService.refreshTradeablePairs().join();
            }
            return exchange.getTradePairSymbol();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to discover assets for " + exchange.getDisplayName(), exception);
        }
    }
}
