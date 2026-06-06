package org.investpro.asset;

import org.investpro.models.trading.TradePair;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

public record AssetCatalogEntry(
        String id,
        ExchangeId exchangeId,
        String symbol,
        String rawExchangeSymbol,
        String baseAsset,
        String quoteAsset,
        AssetType assetType,
        AssetStatus status,
        TradabilityStatus tradabilityStatus,
        boolean orderSubmissionAllowed,
        boolean supportsMarketOrders,
        boolean supportsLimitOrders,
        boolean supportsStopOrders,
        boolean supportsLiveTrading,
        boolean supportsPaperTrading,
        BigDecimal minOrderSize,
        BigDecimal maxOrderSize,
        BigDecimal priceIncrement,
        BigDecimal quantityIncrement,
        BigDecimal baseIncrement,
        BigDecimal quoteIncrement,
        String issuer,
        String homeDomain,
        boolean requiresTrustline,
        boolean trustlineExists,
        boolean liquidityPoolAvailable,
        boolean verified,
        boolean reversedPairSupported,
        boolean manuallyAdded,
        Instant lastSeenAt,
        Instant lastRefreshedAt,
        String metadataJson
) {
    public AssetCatalogEntry {
        exchangeId = exchangeId == null ? ExchangeId.UNKNOWN : exchangeId;
        symbol = normalizeSymbol(symbol, baseAsset, quoteAsset);
        rawExchangeSymbol = blankTo(rawExchangeSymbol, symbol);
        baseAsset = normalizeCode(baseAsset);
        quoteAsset = normalizeCode(quoteAsset);
        assetType = assetType == null ? AssetType.UNKNOWN : assetType;
        status = status == null ? AssetStatus.UNKNOWN : status;
        tradabilityStatus = tradabilityStatus == null ? TradabilityStatus.UNKNOWN : tradabilityStatus;
        lastSeenAt = lastSeenAt == null ? Instant.now() : lastSeenAt;
        lastRefreshedAt = lastRefreshedAt == null ? lastSeenAt : lastRefreshedAt;
        metadataJson = metadataJson == null ? "{}" : metadataJson;
        id = id == null || id.isBlank()
                ? canonicalId(exchangeId, symbol, baseAsset, quoteAsset, issuer)
                : id;
    }

    public static AssetCatalogEntry fromTradePair(ExchangeId exchangeId, TradePair pair, Instant refreshedAt) {
        Objects.requireNonNull(pair, "pair must not be null");
        String base = normalizeCode(pair.getBaseCode());
        String quote = normalizeCode(pair.getCounterCode());
        String symbol = base + "/" + quote;
        String nativeSymbol = pair.getNativeSymbol() == null || pair.getNativeSymbol().isBlank()
                ? symbol
                : pair.getNativeSymbol();
        AssetType type = inferType(exchangeId, base, quote);
        return new AssetCatalogEntry(
                null,
                exchangeId,
                symbol,
                nativeSymbol,
                base,
                quote,
                type,
                AssetStatus.ACTIVE,
                TradabilityStatus.UNKNOWN,
                false,
                true,
                true,
                false,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                "",
                exchangeId == ExchangeId.STELLAR && !"XLM".equals(base),
                false,
                false,
                false,
                exchangeId == ExchangeId.STELLAR,
                false,
                refreshedAt,
                refreshedAt,
                "{}");
    }

    public TradePair toTradePair() {
        try {
            TradePair pair = TradePair.of(baseAsset, quoteAsset);
            pair.setNativeSymbol(rawExchangeSymbol);
            return pair;
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to create TradePair for " + symbol, exception);
        }
    }

    public AssetCatalogEntry seenAt(Instant when) {
        return withStatus(status, when, when);
    }

    public AssetCatalogEntry withStatus(AssetStatus newStatus, Instant seenAt, Instant refreshedAt) {
        return new AssetCatalogEntry(
                id, exchangeId, symbol, rawExchangeSymbol, baseAsset, quoteAsset, assetType,
                newStatus, tradabilityStatus, orderSubmissionAllowed, supportsMarketOrders,
                supportsLimitOrders, supportsStopOrders, supportsLiveTrading, supportsPaperTrading,
                minOrderSize, maxOrderSize, priceIncrement, quantityIncrement, baseIncrement,
                quoteIncrement, issuer, homeDomain, requiresTrustline, trustlineExists,
                liquidityPoolAvailable, verified, reversedPairSupported, manuallyAdded,
                seenAt, refreshedAt, metadataJson);
    }

    public AssetCatalogEntry withTradability(TradabilityStatus status, boolean orderAllowed) {
        return new AssetCatalogEntry(
                id, exchangeId, symbol, rawExchangeSymbol, baseAsset, quoteAsset, assetType,
                this.status, status, orderAllowed, supportsMarketOrders, supportsLimitOrders,
                supportsStopOrders, supportsLiveTrading, supportsPaperTrading, minOrderSize,
                maxOrderSize, priceIncrement, quantityIncrement, baseIncrement, quoteIncrement,
                issuer, homeDomain, requiresTrustline, trustlineExists, liquidityPoolAvailable,
                verified, reversedPairSupported, manuallyAdded, lastSeenAt, lastRefreshedAt,
                metadataJson);
    }

    public static String canonicalId(ExchangeId exchangeId, String symbol, String base, String quote, String issuer) {
        String exchange = exchangeId == null ? ExchangeId.UNKNOWN.id() : exchangeId.id();
        String left = normalizeCode(base);
        String right = normalizeCode(quote);
        String display = normalizeSymbol(symbol, left, right);
        String issuerPart = issuer == null ? "" : issuer.trim().toUpperCase();
        return exchange + ":" + display + (issuerPart.isBlank() ? "" : ":" + issuerPart);
    }

    private static String normalizeSymbol(String symbol, String base, String quote) {
        String text = symbol == null ? "" : symbol.trim().toUpperCase().replace('-', '/').replace('_', '/');
        if (!text.isBlank()) {
            return text;
        }
        return normalizeCode(base) + "/" + normalizeCode(quote);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static AssetType inferType(ExchangeId exchangeId, String base, String quote) {
        if (exchangeId == ExchangeId.STELLAR) {
            return AssetType.STELLAR_ASSET;
        }
        if (exchangeId == ExchangeId.OANDA) {
            return AssetType.FOREX;
        }
        if (exchangeId == ExchangeId.ALPACA) {
            return AssetType.EQUITY;
        }
        if (exchangeId == ExchangeId.COINBASE || exchangeId == ExchangeId.BINANCE_US) {
            return AssetType.CRYPTO;
        }
        if (base.length() == 3 && quote.length() == 3) {
            return AssetType.FOREX;
        }
        return AssetType.UNKNOWN;
    }
}
