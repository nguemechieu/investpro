package org.investpro.models.market;

import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;

import java.time.Instant;
import java.util.Map;

public record MarketInstrument(
        String exchangeId,
        String nativeSymbol,
        String displaySymbol,
        TradePair tradePair,
        AssetClass assetClass,
        MarketType marketType,
        InstrumentType instrumentType,
        LeverageMode leverageMode,
        ContractType contractType,
        String productType,
        ContractExpiryType contractExpiryType,
        UnderlyingType underlyingType,
        TradingEnvironment environment,
        String routingExchange,
        String baseAsset,
        String quoteAsset,
        String marginAsset,
        String underlyingAsset,
        String contractCode,
        String contractSize,
        Instant contractExpiry,
        boolean leveraged,
        boolean expiring,
        boolean physicallySettled,
        boolean cashSettled,
        SymbolTradability tradability,
        Map<String, Object> rawMetadata
) {
    public MarketInstrument {
        exchangeId = clean(exchangeId, "UNKNOWN");
        nativeSymbol = clean(nativeSymbol, "");
        displaySymbol = clean(displaySymbol, nativeSymbol);
        assetClass = assetClass == null ? AssetClass.UNKNOWN : assetClass;
        marketType = marketType == null ? MarketType.UNKNOWN : marketType;
        contractType = contractType == null ? ContractType.UNKNOWN : contractType;
        instrumentType = instrumentType == null || instrumentType == InstrumentType.UNKNOWN
                ? inferInstrumentType(marketType, contractType, assetClass)
                : instrumentType;
        leverageMode = leverageMode == null || leverageMode == LeverageMode.UNKNOWN
                ? inferLeverageMode(marketType, contractType, leveraged)
                : leverageMode;
        productType = clean(productType, "");
        contractExpiryType = contractExpiryType == null ? ContractExpiryType.UNKNOWN : contractExpiryType;
        underlyingType = underlyingType == null ? UnderlyingType.UNKNOWN : underlyingType;
        environment = environment == null ? TradingEnvironment.UNKNOWN : environment;
        routingExchange = clean(routingExchange, "");
        baseAsset = clean(baseAsset, "");
        quoteAsset = clean(quoteAsset, "");
        marginAsset = clean(marginAsset, "");
        underlyingAsset = clean(underlyingAsset, baseAsset);
        contractCode = clean(contractCode, "");
        contractSize = clean(contractSize, "");
        rawMetadata = sanitizeMetadata(rawMetadata);
    }

    public MarketInstrument(
            String exchangeId,
            String nativeSymbol,
            String displaySymbol,
            TradePair tradePair,
            AssetClass assetClass,
            MarketType marketType,
            ContractType contractType,
            String productType,
            ContractExpiryType contractExpiryType,
            UnderlyingType underlyingType,
            TradingEnvironment environment,
            String routingExchange,
            String baseAsset,
            String quoteAsset,
            String marginAsset,
            String underlyingAsset,
            String contractCode,
            String contractSize,
            Instant contractExpiry,
            boolean leveraged,
            boolean expiring,
            boolean physicallySettled,
            boolean cashSettled,
            SymbolTradability tradability,
            Map<String, Object> rawMetadata) {
        this(
                exchangeId,
                nativeSymbol,
                displaySymbol,
                tradePair,
                assetClass,
                marketType,
                inferInstrumentType(marketType, contractType, assetClass),
                inferLeverageMode(marketType, contractType, leveraged),
                contractType,
                productType,
                contractExpiryType,
                underlyingType,
                environment,
                routingExchange,
                baseAsset,
                quoteAsset,
                marginAsset,
                underlyingAsset,
                contractCode,
                contractSize,
                contractExpiry,
                leveraged,
                expiring,
                physicallySettled,
                cashSettled,
                tradability,
                rawMetadata);
    }

    public boolean isSpot() {
        return instrumentType == InstrumentType.SPOT || marketType == MarketType.SPOT;
    }

    public boolean isFuture() {
        return instrumentType == InstrumentType.FUTURE || contractType == ContractType.FUTURE;
    }

    public boolean isPerpetual() {
        return instrumentType == InstrumentType.PERPETUAL
                || contractType == ContractType.PERPETUAL
                || contractExpiryType == ContractExpiryType.PERPETUAL;
    }

    public boolean isDerivative() {
        return instrumentType.isDerivative() || contractType.isDerivative() || leverageMode == LeverageMode.DERIVATIVE_LEVERAGE;
    }

    public boolean canShowInMarketWatch() {
        return tradability == null || tradability.canBeDisplayedInMarketWatch();
    }

    public boolean canBotTrade() {
        return marketType != MarketType.UNKNOWN
                && tradability != null
                && tradability.canBeUsedForBotTrading();
    }

    public String marketBadge() {
        if (assetClass == AssetClass.UNKNOWN && marketType == MarketType.UNKNOWN && contractType == ContractType.UNKNOWN) {
            return "Unknown";
        }
        String asset = title(assetClass.name());
        String contract = switch (instrumentType) {
            case SPOT -> "Spot";
            case FUTURE -> "Future";
            case PERPETUAL -> "Perpetual";
            case OPTION -> "Option";
            case FORWARD -> "Forward";
            case SWAP -> "Swap";
            case CFD -> "CFD";
            case STOCK -> "Stock";
            case ETF -> "ETF";
            case FOREX -> "FX";
            case BOND -> "Bond";
            case FUND -> "Fund";
            case INDEX -> "Index";
            case WARRANT -> "Warrant";
            case COMMODITY -> "Commodity";
            case CRYPTO_SWAP -> "Swap";
            case UNKNOWN -> switch (contractType) {
                case CASH -> "Spot";
                case MARGIN -> "Margin";
                case FUTURE -> "Future";
                case PERPETUAL -> "Perpetual";
                case OPTION -> "Option";
                case FORWARD -> "Forward";
                case SWAP -> "Swap";
                case CFD -> "CFD";
                case NONE, UNKNOWN -> title(marketType.name());
            };
        };
        if (leverageMode == LeverageMode.MARGIN && !contract.contains("Margin")) {
            contract = contract + " Margin";
        }
        if (assetClass == AssetClass.UNKNOWN || contract.isBlank()) {
            return contract;
        }
        return asset + " " + contract;
    }

    private static InstrumentType inferInstrumentType(
            MarketType marketType,
            ContractType contractType,
            AssetClass assetClass) {
        ContractType contract = contractType == null ? ContractType.UNKNOWN : contractType;
        switch (contract) {
            case FUTURE:
                return InstrumentType.FUTURE;
            case PERPETUAL:
                return InstrumentType.PERPETUAL;
            case OPTION:
                return InstrumentType.OPTION;
            case FORWARD:
                return InstrumentType.FORWARD;
            case SWAP:
                return InstrumentType.SWAP;
            case CFD:
                return InstrumentType.CFD;
            case CASH, MARGIN, NONE, UNKNOWN:
                break;
        }

        MarketType market = marketType == null ? MarketType.UNKNOWN : marketType;
        return switch (market) {
            case FUTURE -> InstrumentType.FUTURE;
            case PERPETUAL -> InstrumentType.PERPETUAL;
            case OPTION -> InstrumentType.OPTION;
            case CFD -> InstrumentType.CFD;
            case CRYPTO_SWAP -> InstrumentType.CRYPTO_SWAP;
            case STOCK -> InstrumentType.STOCK;
            case ETF -> InstrumentType.ETF;
            case INDEX -> InstrumentType.INDEX;
            case BOND -> InstrumentType.BOND;
            case FUND -> InstrumentType.FUND;
            case WARRANT -> InstrumentType.WARRANT;
            case FX, FOREX -> InstrumentType.FOREX;
            case SPOT, MARGIN, CRYPTO -> InstrumentType.SPOT;
            case SECURITIES -> inferSecurityInstrument(assetClass);
            case DERIVATIVE, DERIVATIVES, OTC, SYNTHETIC, UNKNOWN -> InstrumentType.UNKNOWN;
        };
    }

    private static InstrumentType inferSecurityInstrument(AssetClass assetClass) {
        return switch (assetClass == null ? AssetClass.UNKNOWN : assetClass) {
            case EQUITY -> InstrumentType.STOCK;
            case ETF -> InstrumentType.ETF;
            case INDEX -> InstrumentType.INDEX;
            case FUND -> InstrumentType.FUND;
            case BOND -> InstrumentType.BOND;
            case COMMODITY, METAL -> InstrumentType.COMMODITY;
            default -> InstrumentType.UNKNOWN;
        };
    }

    private static LeverageMode inferLeverageMode(
            MarketType marketType,
            ContractType contractType,
            boolean leveraged) {
        ContractType contract = contractType == null ? ContractType.UNKNOWN : contractType;
        if (contract.isDerivative()) {
            return LeverageMode.DERIVATIVE_LEVERAGE;
        }
        if (contract == ContractType.MARGIN || marketType == MarketType.MARGIN) {
            return LeverageMode.MARGIN;
        }
        return leveraged ? LeverageMode.MARGIN : LeverageMode.NONE;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> sanitized = new java.util.LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static String title(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equals(value)) {
            return "";
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
