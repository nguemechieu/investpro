package org.investpro.asset;

import org.investpro.models.trading.TradePair;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public static AssetCatalogEntry fromMarketInstrument(
            ExchangeId exchangeId,
            MarketInstrument instrument,
            Instant refreshedAt) {
        Objects.requireNonNull(instrument, "instrument must not be null");
        TradePair pair = instrument.tradePair();
        String base = !instrument.baseAsset().isBlank()
                ? normalizeCode(instrument.baseAsset())
                : pair == null ? "" : normalizeCode(pair.getBaseCode());
        String quote = !instrument.quoteAsset().isBlank()
                ? normalizeCode(instrument.quoteAsset())
                : pair == null ? "" : normalizeCode(pair.getCounterCode());
        String symbol = !instrument.displaySymbol().isBlank()
                ? instrument.displaySymbol()
                : pair == null ? instrument.nativeSymbol() : pair.toString('/');
        String nativeSymbol = instrument.nativeSymbol().isBlank() ? symbol : instrument.nativeSymbol();

        return new AssetCatalogEntry(
                null,
                exchangeId,
                symbol,
                nativeSymbol,
                base,
                quote,
                inferType(
                        instrument.assetClass(),
                        instrument.instrumentType(),
                        instrument.contractType(),
                        instrument.marketType(),
                        exchangeId,
                        base,
                        quote),
                AssetStatus.ACTIVE,
                mapTradability(instrument),
                instrument.tradability() != null && instrument.tradability().orderSubmissionAllowed(),
                instrument.tradability() == null || instrument.tradability().marketOrderAllowed(),
                instrument.tradability() == null || instrument.tradability().limitOrderAllowed(),
                instrument.tradability() != null && instrument.tradability().stopOrderAllowed(),
                instrument.tradability() == null || instrument.tradability().liveTradingAllowed(),
                instrument.tradability() == null || instrument.tradability().paperTradingAllowed(),
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                false,
                refreshedAt,
                refreshedAt,
                metadataJson(instrument));
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

    private static AssetType inferType(
            AssetClass assetClass,
            InstrumentType instrumentType,
            ContractType contractType,
            MarketType marketType,
            ExchangeId exchangeId,
            String base,
            String quote) {
        if (instrumentType != null && instrumentType.isDerivative()) {
            return instrumentType == InstrumentType.CFD ? AssetType.CFD : AssetType.FUTURE;
        }
        if (instrumentType != null) {
            switch (instrumentType) {
                case FOREX:
                    return AssetType.FOREX;
                case STOCK, ETF, INDEX, FUND, WARRANT:
                    return AssetType.EQUITY;
                case COMMODITY:
                    return AssetType.FUTURE;
                default:
                    break;
            }
        }
        if (contractType != null && contractType.isDerivative()) {
            return AssetType.FUTURE;
        }
        if (marketType != null && marketType.isFx()) {
            return AssetType.FOREX;
        }
        if (marketType == MarketType.FUTURE
                || marketType == MarketType.PERPETUAL
                || marketType == MarketType.OPTION
                || marketType == MarketType.CRYPTO_SWAP) {
            return AssetType.FUTURE;
        }
        if (marketType == MarketType.CFD) {
            return AssetType.CFD;
        }
        if (marketType == MarketType.STOCK
                || marketType == MarketType.ETF
                || marketType == MarketType.INDEX
                || marketType == MarketType.FUND
                || marketType == MarketType.WARRANT) {
            return AssetType.EQUITY;
        }
        if (assetClass == null || assetClass == AssetClass.UNKNOWN) {
            return inferType(exchangeId, base, quote);
        }
        return switch (assetClass) {
            case CRYPTO -> AssetType.CRYPTO;
            case FIAT -> AssetType.FOREX;
            case EQUITY, INDEX, ETF, FUND -> AssetType.EQUITY;
            case COMMODITY, METAL -> AssetType.FUTURE;
            case BOND, SYNTHETIC, UNKNOWN -> AssetType.UNKNOWN;
        };
    }

    private static TradabilityStatus mapTradability(MarketInstrument instrument) {
        if (instrument.tradability() == null || instrument.tradability().status() == null) {
            return TradabilityStatus.UNKNOWN;
        }
        try {
            return TradabilityStatus.valueOf(instrument.tradability().status().name());
        } catch (Exception exception) {
            return TradabilityStatus.UNKNOWN;
        }
    }

    private static String metadataJson(MarketInstrument instrument) {
        Map<String, Object> metadata = new LinkedHashMap<>(instrument.rawMetadata());
        metadata.put("asset_class", instrument.assetClass().name());
        metadata.put("market_type", instrument.marketType().name());
        metadata.put("instrument_type", instrument.instrumentType().name());
        metadata.put("leverage_mode", instrument.leverageMode().name());
        metadata.put("contract_type", instrument.contractType().name());
        metadata.put("product_type", instrument.productType());
        metadata.put("contract_expiry_type", instrument.contractExpiryType().name());
        metadata.put("underlying_type", instrument.underlyingType().name());
        metadata.put("routing_exchange", instrument.routingExchange());
        metadata.put("contract_code", instrument.contractCode());
        metadata.put("contract_size", instrument.contractSize());
        metadata.put("contract_expiry", instrument.contractExpiry() == null ? "" : instrument.contractExpiry().toString());
        metadata.put("leveraged", Boolean.toString(instrument.leveraged()));
        metadata.put("expiring", Boolean.toString(instrument.expiring()));
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(escapeJson(String.valueOf(entry.getValue()))).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
