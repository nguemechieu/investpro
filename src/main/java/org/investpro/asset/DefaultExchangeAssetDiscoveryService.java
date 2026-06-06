package org.investpro.asset;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.market.MarketInstrument;
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
        return CompletableFuture.supplyAsync(() -> fetchInstruments(exchange), executor)
                .thenApply(instruments -> instruments.stream()
                        .filter(instrument -> instrument != null)
                        .map(instrument -> AssetCatalogEntry.fromMarketInstrument(exchangeId, instrument, Instant.now()))
                        .toList());
    }

    private List<MarketInstrument> fetchInstruments(Exchange exchange) {
        try {
            List<MarketInstrument> instruments = exchange.fetchMarketInstruments().join();
            if (instruments != null && !instruments.isEmpty()) {
                return instruments;
            }
            return org.investpro.trading.market.MarketInstrumentService
                    .legacyTradePairsToInstruments(exchange, fetchPairs(exchange));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to discover market instruments for "
                    + exchange.getDisplayName(), exception);
        }
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
