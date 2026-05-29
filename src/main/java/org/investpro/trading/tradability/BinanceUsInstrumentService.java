package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class BinanceUsInstrumentService implements ExchangeInstrumentService {

    private static final String EXCHANGE_ID = "binance-us";
    private static final String ACCOUNT_ID = null;

    private final Exchange exchange;
    private final TradeablePairCache cache = TradeablePairCache.getInstance();

    public BinanceUsInstrumentService(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public String getExchangeId() {
        return EXCHANGE_ID;
    }

    @Override
    public CompletableFuture<List<TradePair>> getTradeablePairs() {
        Optional<List<TradePair>> cached = cache.get(EXCHANGE_ID, ACCOUNT_ID);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        Optional<List<TradePair>> stale = cache.getWithStaleWarning(EXCHANGE_ID, ACCOUNT_ID);
        if (stale.isPresent()) {
            refreshInBackground();
            return CompletableFuture.completedFuture(stale.get());
        }

        return refreshTradeablePairs();
    }

    @Override
    public CompletableFuture<List<TradePair>> refreshTradeablePairs() {
        cache.forceInvalidate(EXCHANGE_ID, ACCOUNT_ID);
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return exchange.getTradablePairs();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to fetch tradeable pairs for " + EXCHANGE_ID, e);
                    }
                })
                .thenApply(pairs -> {
                    cache.put(EXCHANGE_ID, ACCOUNT_ID, pairs);
                    log.info("[{}] Refreshed {} tradeable pairs", EXCHANGE_ID, pairs.size());
                    return pairs;
                });
    }

    @Override
    public CompletableFuture<Boolean> isPairSupported(TradePair pair) {
        return getTradeablePairs().thenApply(pairs -> pairs.stream()
                .anyMatch(p -> p.equals(pair)));
    }

    @Override
    public CompletableFuture<InstrumentTradeStatus> getTradeStatus(TradePair pair) {
        return exchange.fetchTradabilityStatus(pair)
                .thenApply(st -> InstrumentTradeStatus.from(st.status()));
    }

    @Override
    public CompletableFuture<InstrumentTradabilityReport> explainTradability(TradePair pair) {
        return exchange.fetchTradabilityStatus(pair)
                .thenApply(st -> InstrumentTradabilityReport.from(
                        st,
                        EXCHANGE_ID,
                        pair.toString('/'),
                        pair.toSlashSymbol(),
                        pair.toSlashSymbol(),
                        "exchange.fetchTradabilityStatus"));
    }

    @Override
    public void invalidateCache() {
        cache.forceInvalidate(EXCHANGE_ID, ACCOUNT_ID);
        log.debug("[{}] Cache invalidated", EXCHANGE_ID);
    }

    private void refreshInBackground() {
        refreshTradeablePairs().exceptionally(ex -> {
            log.warn("[{}] Background refresh failed: {}", EXCHANGE_ID, ex.getMessage());
            return List.of();
        });
    }
}
