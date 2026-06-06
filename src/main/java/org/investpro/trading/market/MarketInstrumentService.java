package org.investpro.trading.market;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.TradingEnvironment;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
public class MarketInstrumentService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final Map<String, CacheEntry> cacheByExchange = new ConcurrentHashMap<>();
    private final Duration ttl;

    public MarketInstrumentService() {
        this(DEFAULT_TTL);
    }

    public MarketInstrumentService(Duration ttl) {
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }

    public CompletableFuture<List<MarketInstrument>> loadForExchange(Exchange exchange) {
        if (exchange == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        String exchangeId = normalizeExchangeId(exchange.getExchangeId());
        CacheEntry cached = cacheByExchange.get(exchangeId);
        if (cached != null && !cached.expired()) {
            return CompletableFuture.completedFuture(cached.instruments());
        }

        return exchange.fetchMarketInstruments()
                .thenApply(instruments -> {
                    List<MarketInstrument> safe = instruments == null ? List.of() : List.copyOf(instruments);
                    cacheByExchange.put(exchangeId, new CacheEntry(safe, Instant.now().plus(ttl)));
                    return safe;
                });
    }

    public List<MarketInstrument> instruments() {
        return cacheByExchange.values().stream()
                .filter(entry -> !entry.expired())
                .flatMap(entry -> entry.instruments().stream())
                .sorted(Comparator.comparing(MarketInstrument::exchangeId)
                        .thenComparing(MarketInstrument::displaySymbol))
                .toList();
    }

    public Optional<MarketInstrument> findByNativeSymbol(String exchangeId, String nativeSymbol) {
        String targetExchange = normalizeExchangeId(exchangeId);
        String targetSymbol = normalize(nativeSymbol);
        return instruments().stream()
                .filter(instrument -> normalizeExchangeId(instrument.exchangeId()).equals(targetExchange))
                .filter(instrument -> normalize(instrument.nativeSymbol()).equals(targetSymbol))
                .findFirst();
    }

    public Optional<MarketInstrument> findByTradePair(String exchangeId, TradePair pair) {
        if (pair == null) {
            return Optional.empty();
        }
        String targetExchange = normalizeExchangeId(exchangeId);
        String targetPair = normalize(pair.toString('/'));
        String targetNative = normalize(pair.getNativeSymbol());
        return instruments().stream()
                .filter(instrument -> normalizeExchangeId(instrument.exchangeId()).equals(targetExchange))
                .filter(instrument -> {
                    TradePair instrumentPair = instrument.tradePair();
                    return instrumentPair != null
                            && (normalize(instrumentPair.toString('/')).equals(targetPair)
                            || (!targetNative.isBlank()
                            && normalize(instrument.nativeSymbol()).equals(targetNative)));
                })
                .findFirst();
    }

    public List<MarketInstrument> filterByMarketType(MarketType marketType) {
        MarketType target = marketType == null ? MarketType.UNKNOWN : marketType;
        return instruments().stream()
                .filter(instrument -> instrument.marketType() == target)
                .toList();
    }

    public List<MarketInstrument> search(String text) {
        String query = normalize(text);
        if (query.isBlank()) {
            return instruments();
        }
        return instruments().stream()
                .filter(instrument -> normalize(instrument.nativeSymbol()).contains(query)
                        || normalize(instrument.displaySymbol()).contains(query)
                        || normalize(instrument.baseAsset()).contains(query)
                        || normalize(instrument.quoteAsset()).contains(query))
                .toList();
    }

    public List<MarketInstrument> tradableOnly() {
        return instruments().stream()
                .filter(instrument -> instrument.tradability() != null)
                .filter(instrument -> instrument.tradability().status() == TradabilityStatus.FULLY_TRADABLE)
                .toList();
    }

    public List<MarketInstrument> marketDataAllowedOnly() {
        return instruments().stream()
                .filter(MarketInstrument::canShowInMarketWatch)
                .toList();
    }

    public List<MarketInstrument> botTradableOnly() {
        return instruments().stream()
                .filter(MarketInstrument::canBotTrade)
                .toList();
    }

    public void invalidateExchange(String exchangeId) {
        cacheByExchange.remove(normalizeExchangeId(exchangeId));
    }

    public static List<MarketInstrument> legacyTradePairsToInstruments(Exchange exchange, List<TradePair> pairs) {
        if (exchange == null || pairs == null || pairs.isEmpty()) {
            return List.of();
        }
        String exchangeId = normalizeExchangeId(exchange.getExchangeId());
        List<MarketInstrument> result = new ArrayList<>();
        for (TradePair pair : pairs) {
            if (pair == null) {
                continue;
            }
            SymbolTradability tradability = null;
            try {
                tradability = exchange.fetchTradabilityStatus(pair).getNow(null);
            } catch (Exception ignored) {
                log.info(
                        "Tradability was ignored"
                );
            }
            result.add(new MarketInstrument(
                    exchangeId,
                    pair.getNativeSymbol() == null || pair.getNativeSymbol().isBlank()
                            ? pair.toString('-')
                            : pair.getNativeSymbol(),
                    pair.toString('/'),
                    pair,
                    AssetClass.UNKNOWN,
                    MarketType.SPOT,
                    ContractType.CASH,
                    "SPOT",
                    null,
                    null,
                    TradingEnvironment.UNKNOWN,
                    "",
                    pair.getBaseCode(),
                    pair.getCounterCode(),
                    pair.getCounterCode(),
                    pair.getBaseCode(),
                    "",
                    "",
                    null,
                    false,
                    false,
                    true,
                    false,
                    tradability,
                    new LinkedHashMap<>(Map.of("source", "legacy-trade-pair"))));
        }
        return result;
    }

    private static String normalizeExchangeId(String exchangeId) {
        return normalize(exchangeId == null || exchangeId.isBlank() ? "UNKNOWN" : exchangeId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record CacheEntry(List<MarketInstrument> instruments, Instant expiresAt) {
        CacheEntry {
            instruments = instruments == null ? List.of() : List.copyOf(instruments);
            expiresAt = Objects.requireNonNullElseGet(expiresAt, Instant::now);
        }

        boolean expired() {
            return !Instant.now().isBefore(expiresAt);
        }
    }
}
