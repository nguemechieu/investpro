package org.investpro.ui.theme;

import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.LeverageMode;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.ProductVenue;
import org.investpro.models.market.TradingEnvironment;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public record MarketConfiguration(
        String username,
        String marketType,
        String venue,
        String exchange,
        String apiKey,
        String apiSecret,
        String accountId,
        String telegramToken,
        String openaiApiKey,
        String openaiModel,
        String openaiOrgId,
        String tradingMode) {
    public String telegramToken() {
        return telegramToken;

    }

    public String openaiApiKey() {
        return openaiApiKey;
    }

    public String openaiModel() {
        return openaiModel;
    }



    @Contract(pure = true)
    public @NonNull String tradingMode() {
        if (tradingMode == null || tradingMode.isBlank()) {
            return "PAPER";
        }
        String normalized = tradingMode.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("LIVE") ? "LIVE" : "PAPER";
    }

    public boolean isPaperTrading() {
        return "PAPER".equalsIgnoreCase(tradingMode());
    }

    public boolean hasOpenAiConfiguration() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }

    @Contract(pure = true)
    public @NonNull String selectedTradingMode() {
        return tradingMode();
    }

    public @NonNull MarketType normalizedMarketType() {
        String normalized = normalizeLabel(marketType);
        return switch (normalized) {
            case "CRYPTO", "CRYPTO_SPOT", "SPOT" ->
                    MarketType.SPOT;
            case "STOCK", "STOCKS", "EQUITY", "EQUITIES", "ETF", "ETFS", "BOND", "BONDS" ->
                    MarketType.SECURITIES;
            case "PERPETUAL", "PERPETUALS", "PERP", "PERPS", "FUTURE", "FUTURES", "US_FUTURES", "OPTION", "OPTIONS",
                    "INDEX", "INDICES", "COMMODITY", "COMMODITIES" -> MarketType.DERIVATIVES;
            case "FOREX", "FX" -> MarketType.FOREX;
            default -> MarketType.UNKNOWN;
        };
    }

    public @NonNull InstrumentType normalizedInstrumentType() {
        String normalized = normalizeLabel(marketType);
        return switch (normalized) {
            case "CRYPTO", "CRYPTO_SPOT", "SPOT" -> InstrumentType.SPOT;
            case "PERPETUAL", "PERPETUALS", "PERP", "PERPS" -> InstrumentType.PERPETUAL;
            case "FUTURE", "FUTURES", "US_FUTURES" -> InstrumentType.FUTURE;
            case "OPTION", "OPTIONS" -> InstrumentType.OPTION;
            case "FOREX", "FX" -> InstrumentType.FOREX;
            case "STOCK", "STOCKS", "EQUITY", "EQUITIES" -> InstrumentType.STOCK;
            case "ETF", "ETFS" -> InstrumentType.ETF;
            case "INDEX", "INDICES" -> InstrumentType.INDEX;
            case "COMMODITY", "COMMODITIES" -> InstrumentType.COMMODITY;
            case "BOND", "BONDS" -> InstrumentType.BOND;
            default -> InstrumentType.UNKNOWN;
        };
    }

    public @NonNull LeverageMode normalizedLeverageMode() {
        String normalized = normalizeLabel(marketType);
        if (normalized.contains("MARGIN")) {
            return LeverageMode.MARGIN;
        }
        InstrumentType instrumentType = normalizedInstrumentType();
        if (instrumentType.isDerivative()) {
            return LeverageMode.DERIVATIVE_LEVERAGE;
        }
        return LeverageMode.NONE;
    }

    public @NonNull AssetClass normalizedAssetClass() {
        String normalized = normalizeLabel(marketType);
        return switch (normalized) {
            case "CRYPTO", "CRYPTO_SPOT", "SPOT", "PERPETUAL", "PERPETUALS", "PERP", "PERPS", "FUTURE", "FUTURES",
                    "US_FUTURES", "OPTION", "OPTIONS" -> AssetClass.CRYPTO;
            case "FOREX", "FX" -> AssetClass.FIAT;
            case "STOCK", "STOCKS", "EQUITY", "EQUITIES" -> AssetClass.EQUITY;
            case "INDEX", "INDICES" -> AssetClass.INDEX;
            case "COMMODITY", "COMMODITIES" -> AssetClass.COMMODITY;
            case "ETF", "ETFS" -> AssetClass.ETF;
            case "BOND", "BONDS" -> AssetClass.BOND;
            default -> AssetClass.UNKNOWN;
        };
    }

    public @NonNull ContractType normalizedContractType() {
        String normalized = normalizeLabel(marketType);
        return switch (normalized) {
            case "CRYPTO", "CRYPTO_SPOT", "SPOT", "STOCK", "STOCKS", "EQUITY", "EQUITIES", "ETF", "ETFS", "BOND", "BONDS" ->
                    ContractType.CASH;
            case "PERPETUAL", "PERPETUALS", "PERP", "PERPS" -> ContractType.PERPETUAL;
            case "FUTURE", "FUTURES", "US_FUTURES", "INDEX", "INDICES", "COMMODITY", "COMMODITIES" ->
                    ContractType.FUTURE;
            case "OPTION", "OPTIONS" -> ContractType.OPTION;
            case "FOREX", "FX" -> ContractType.CFD;
            default -> ContractType.UNKNOWN;
        };
    }

    public @NonNull ProductVenue normalizedVenue() {
        String exchangeKey = normalizeLabel(exchange);
        String venueKey = normalizeLabel(venue);

        ProductVenue directVenue = directVenueAlias(exchangeKey, venueKey);
        if (directVenue != ProductVenue.UNKNOWN) {
            return directVenue;
        }

        if (exchangeKey.contains("COINBASE")) {
            MarketType selectedMarketType = normalizedMarketType();
            if (venueKey.contains("INTERNATIONAL") || normalizedContractType() == ContractType.PERPETUAL) {
                return ProductVenue.COINBASE_INTERNATIONAL;
            }
            if (venueKey.contains("DERIVATIVE")
                    || (selectedMarketType != MarketType.SPOT && selectedMarketType != MarketType.UNKNOWN)) {
                return ProductVenue.COINBASE_DERIVATIVES;
            }
            return ProductVenue.COINBASE_ADVANCED;
        }

        if (exchangeKey.contains("OANDA")) {
            return ProductVenue.OANDA;
        }

        if (exchangeKey.contains("BINANCE")) {
            return exchangeKey.contains("US") ? ProductVenue.BINANCE_US_SPOT : ProductVenue.BINANCE_SPOT;
        }

        if (exchangeKey.contains("KRAKEN")) {
            return ProductVenue.KRAKEN_SPOT;
        }

        if (exchangeKey.contains("BITFINEX")) {
            return exchangeKey.contains("US") ? ProductVenue.BITFINEX_US_SPOT : ProductVenue.BITFINEX_SPOT;
        }

        if (exchangeKey.contains("BITMEX")) {
            return ProductVenue.BITMEX_DERIVATIVES;
        }

        if (exchangeKey.contains("KUCOIN")) {
            return exchangeKey.contains("US") ? ProductVenue.KUCOIN_US_SPOT : ProductVenue.KUCOIN_SPOT;
        }

        if (exchangeKey.contains("BITSTAMP")) {
            return ProductVenue.BITSTAMP_SPOT;
        }

        if (exchangeKey.contains("POLONIEX")) {
            return ProductVenue.POLONIEX_SPOT;
        }

        if (exchangeKey.contains("BITTREX")) {
            return ProductVenue.BITTREX_SPOT;
        }

        if (exchangeKey.equals("IG")) {
            return ProductVenue.IG;
        }

        if (exchangeKey.contains("SCHWAB")) {
            return ProductVenue.SCHWAB_EQUITIES;
        }

        if (exchangeKey.contains("ALPACA")) {
            return normalizedAssetClass() == AssetClass.CRYPTO
                    ? ProductVenue.ALPACA_CRYPTO
                    : ProductVenue.ALPACA_EQUITIES;
        }

        if (exchangeKey.contains("INTERACTIVE_BROKERS") || exchangeKey.contains("IBKR")) {
            if (normalizedMarketType().isFx()) {
                return ProductVenue.IBKR_IDEALPRO;
            }
            if (normalizedContractType() == ContractType.FUTURE || normalizedAssetClass() == AssetClass.COMMODITY) {
                return ProductVenue.IBKR_CME;
            }
            return ProductVenue.IBKR_SMART;
        }

        if (exchangeKey.contains("STELLAR")) {
            return ProductVenue.STELLAR_DEX;
        }
        if (exchangeKey.contains("SOLONA") || exchangeKey.contains("SOLANA")) {
            return ProductVenue.SOLANA_DEX;
        }

        return ProductVenue.UNKNOWN;
    }

    private ProductVenue directVenueAlias(String exchangeKey, String venueKey) {
        String combined = (exchangeKey + "_" + venueKey).replaceAll("_+", "_");
        ProductVenue byVenue = switch (venueKey) {
            case "COINBASE_ADVANCED", "COINBASE_SPOT", "COINBASE_US_SPOT" -> ProductVenue.COINBASE_ADVANCED;
            case "COINBASE_DERIVATIVES", "COINBASE_US_DERIVATIVES", "COINBASE_FUTURES" ->
                    ProductVenue.COINBASE_DERIVATIVES;
            case "COINBASE_INTERNATIONAL", "COINBASE_PERPETUALS", "COINBASE_PERPS" ->
                    ProductVenue.COINBASE_INTERNATIONAL;
            case "OANDA", "OANDA_GLOBAL", "OANDA_FX" -> ProductVenue.OANDA;
            case "BINANCE_SPOT" -> ProductVenue.BINANCE_SPOT;
            case "BINANCE_US", "BINANCE_US_SPOT" -> ProductVenue.BINANCE_US_SPOT;
            case "ALPACA_EQUITIES", "ALPACA_STOCKS" -> ProductVenue.ALPACA_EQUITIES;
            case "ALPACA_CRYPTO" -> ProductVenue.ALPACA_CRYPTO;
            case "IBKR_SMART", "INTERACTIVE_BROKERS_SMART", "IB_SMART" -> ProductVenue.IBKR_SMART;
            case "IBKR_IDEALPRO", "INTERACTIVE_BROKERS_IDEALPRO", "IBKR_FX" -> ProductVenue.IBKR_IDEALPRO;
            case "IBKR_CME", "INTERACTIVE_BROKERS_CME", "IBKR_FUTURES" -> ProductVenue.IBKR_CME;
            case "STELLAR_DEX", "STELLAR_NETWORK_DEX" -> ProductVenue.STELLAR_DEX;
            case "SOLANA_DEX", "SOLONA_NETWORK_DEX" -> ProductVenue.SOLANA_DEX;
            default -> ProductVenue.UNKNOWN;
        };
        if (byVenue != ProductVenue.UNKNOWN) {
            return byVenue;
        }
        return switch (combined) {
            case "COINBASE_ADVANCED", "COINBASE_SPOT", "COINBASE_US_SPOT" -> ProductVenue.COINBASE_ADVANCED;
            case "COINBASE_DERIVATIVES", "COINBASE_US_DERIVATIVES", "COINBASE_FUTURES" ->
                    ProductVenue.COINBASE_DERIVATIVES;
            case "COINBASE_INTERNATIONAL", "COINBASE_PERPETUALS", "COINBASE_PERPS" ->
                    ProductVenue.COINBASE_INTERNATIONAL;
            case "OANDA", "OANDA_GLOBAL", "OANDA_FX" -> ProductVenue.OANDA;
            case "BINANCE", "BINANCE_SPOT" -> ProductVenue.BINANCE_SPOT;
            case "BINANCE_US", "BINANCE_US_SPOT" -> ProductVenue.BINANCE_US_SPOT;
            case "ALPACA_EQUITIES", "ALPACA_STOCKS" -> ProductVenue.ALPACA_EQUITIES;
            case "ALPACA_CRYPTO" -> ProductVenue.ALPACA_CRYPTO;
            case "IBKR_SMART", "INTERACTIVE_BROKERS_SMART", "IB_SMART" -> ProductVenue.IBKR_SMART;
            case "IBKR_IDEALPRO", "INTERACTIVE_BROKERS_IDEALPRO", "IBKR_FX" -> ProductVenue.IBKR_IDEALPRO;
            case "IBKR_CME", "INTERACTIVE_BROKERS_CME", "IBKR_FUTURES" -> ProductVenue.IBKR_CME;
            case "STELLAR_DEX", "STELLAR_NETWORK_DEX" -> ProductVenue.STELLAR_DEX;
            case "SOLANA_DEX", "SOLONA_NETWORK_DEX" -> ProductVenue.SOLANA_DEX;
            default -> ProductVenue.UNKNOWN;
        };
    }

    public @NonNull TradingEnvironment normalizedEnvironment() {
        String mode = tradingMode == null ? "" : tradingMode.trim().toUpperCase(Locale.ROOT);
        if (mode.contains("LIVE")) {
            return TradingEnvironment.LIVE;
        }
        if (mode.contains("PRACTICE")) {
            return TradingEnvironment.PRACTICE;
        }
        if (mode.contains("SANDBOX")) {
            return TradingEnvironment.SANDBOX;
        }
        if (mode.contains("TESTNET")) {
            return TradingEnvironment.TESTNET;
        }
        if (mode.contains("BACKTEST")) {
            return TradingEnvironment.BACKTEST;
        }
        if (mode.contains("SIMULATION") || mode.contains("SIMULATED")) {
            return TradingEnvironment.SIMULATION;
        }
        if (mode.contains("PAPER") || mode.isBlank()) {
            return TradingEnvironment.PAPER;
        }
        return TradingEnvironment.UNKNOWN;
    }

    private static String normalizeLabel(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toUpperCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace('/', '_')
                        .replaceAll("[^A-Z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
    }
}
