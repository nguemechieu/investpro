package org.investpro.market;

import org.investpro.decision.AssetMarketType;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.FiatCurrency;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

/**
 * Detects asset market type from TradePair and symbol heuristics.
 */
public class AssetMarketTypeDetector {

    private static final Set<String> STABLES = Set.of("USDT", "USDC", "DAI", "BUSD", "FDUSD", "TUSD");
    private static final Set<String> CRYPTO_BASES = Set.of("BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "BNB", "AVAX",
            "DOT");

    @NotNull
    public AssetMarketType detect(@NotNull TradePair tradePair) {
        String symbol = tradePair.getSymbol().toUpperCase(Locale.ROOT);
        String baseCode = upper(tradePair.getBaseCode());
        String counterCode = upper(tradePair.getCounterCode());

        boolean baseCrypto = tradePair.getBaseCurrency() instanceof CryptoCurrency;
        boolean counterCrypto = tradePair.getCounterCurrency() instanceof CryptoCurrency;
        boolean baseFiat = tradePair.getBaseCurrency() instanceof FiatCurrency;
        boolean counterFiat = tradePair.getCounterCurrency() instanceof FiatCurrency;

        if (baseCrypto && counterCrypto) {
            return AssetMarketType.CRYPTO_SPOT;
        }
        if (baseCrypto && (counterFiat || STABLES.contains(counterCode))) {
            return AssetMarketType.CRYPTO_SPOT;
        }
        if (baseFiat && counterFiat && looksLikeFxPair(baseCode, counterCode, symbol)) {
            return AssetMarketType.FOREX;
        }

        if (looksLikeCryptoSymbol(baseCode, counterCode, symbol)) {
            return AssetMarketType.CRYPTO_SPOT;
        }

        if (looksLikeDerivativeSymbol(symbol)) {
            if (looksLikeCryptoSymbol(baseCode, counterCode, symbol)) {
                return AssetMarketType.CRYPTO_DERIVATIVES;
            }
            return AssetMarketType.EQUITY_DERIVATIVES;
        }

        if (symbol.matches("^[A-Z]{1,6}$")) {
            return AssetMarketType.EQUITIES;
        }

        return AssetMarketType.UNKNOWN;
    }

    private boolean looksLikeFxPair(String baseCode, String counterCode, String symbol) {
        return (baseCode.length() == 3 && counterCode.length() == 3)
                || symbol.matches("^[A-Z]{3}[/_][A-Z]{3}$");
    }

    private boolean looksLikeCryptoSymbol(String baseCode, String counterCode, String symbol) {
        return CRYPTO_BASES.contains(baseCode)
                || CRYPTO_BASES.contains(counterCode)
                || STABLES.contains(baseCode)
                || STABLES.contains(counterCode)
                || symbol.contains("PERP");
    }

    private boolean looksLikeDerivativeSymbol(String symbol) {
        String s = symbol.toUpperCase(Locale.ROOT);
        return s.contains("PERP") || s.contains("FUT") || s.contains("OPT") || s.endsWith(".P");
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
