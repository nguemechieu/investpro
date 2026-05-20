package org.investpro.ui.utils;

import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads currency flag / icon images for display in market watch tables.
 * <p>
 * Looks up bundled icons from {@code /currency} first, then older icon paths.
 */
@Slf4j
public final class CurrencyIconLoader {

    private static final Map<String, String> SYMBOL_ALIASES = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("XBT", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("XLM", "stellar"),
            Map.entry("USDC", "usd-coin"),
            Map.entry("USDT", "tether"),
            Map.entry("XRP", "ripple"),
            Map.entry("SOL", "solana"),
            Map.entry("ADA", "cardano"),
            Map.entry("BNB", "binancecoin"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("LTC", "litecoin"),
            Map.entry("DOT", "polkadot"),
            Map.entry("LINK", "chainlink"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("MATIC", "matic-network"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("UNI", "uniswap"),
            Map.entry("DAI", "dai"),
            Map.entry("NEAR", "near"),
            Map.entry("ICP", "internet-computer"),
            Map.entry("AAVE", "aave"),
            Map.entry("ALGO", "algorand"),
            Map.entry("APE", "apecoin"),
            Map.entry("APT", "aptos"),
            Map.entry("ARB", "arbitrum"),
            Map.entry("BCH", "bitcoin-cash"),
            Map.entry("ETC", "ethereum-classic"),
            Map.entry("FIL", "filecoin"),
            Map.entry("HBAR", "hedera-hashgraph"),
            Map.entry("INJ", "injective-protocol"),
            Map.entry("PEPE", "pepe"),
            Map.entry("SHIB", "shiba-inu"),
            Map.entry("SUI", "sui"),
            Map.entry("TRX", "tron"),
            Map.entry("WBTC", "wrapped-bitcoin"));

    private static final String FALLBACK_PATH = "/icons/currencies/unknown.png";

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private CurrencyIconLoader() {
    }

    /**
     * Load the icon for the given currency code (e.g. "BTC", "USD").
     *
     * @param currencyCode ISO or crypto currency code
     * @return Image, or {@code null} if neither the icon nor the fallback is found
     */
    public static Image loadCurrencyIcon(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return loadFallback();
        }

        String code = currencyCode.trim().toUpperCase();
        return CACHE.computeIfAbsent(code, key -> {
            Image icon = candidates(key).stream()
                    .map(CurrencyIconLoader::tryLoad)
                    .filter(image -> image != null && !image.isError())
                    .findFirst()
                    .orElse(null);
            if (icon != null) {
                return icon;
            }
            log.debug("Currency icon not found for {}, using fallback.", key);
            return loadFallback();
        });
    }

    private static Image loadFallback() {
        return CACHE.computeIfAbsent("__fallback__", ignored -> tryLoad(FALLBACK_PATH));
    }

    private static Image tryLoad(String resourcePath) {
        try {
            InputStream stream = CurrencyIconLoader.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                return null;
            }
            return new Image(stream);
        } catch (Exception e) {
            log.debug("Failed to load icon from {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    private static List<String> candidates(String key) {
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        String lower = normalized.toLowerCase(Locale.ROOT);
        String alias = SYMBOL_ALIASES.getOrDefault(normalized, lower);
        return List.of(
                "/currency/" + alias + ".jpg",
                "/currency/" + normalized + ".jpg",
                "/currency/" + lower + ".jpg",
                "/currency/" + prettify(normalized) + ".jpg",
                "/icons/currencies/" + normalized + ".png",
                "/icons/currencies/" + lower + ".png");
    }

    private static String prettify(String key) {
        return switch (key) {
            case "BCH" -> "Bitcoin Cash";
            case "ETC" -> "Ethereum Classic";
            case "ATOM" -> "Cosmos Hub";
            case "HBAR" -> "Hedera";
            case "ICP" -> "Internet Computer";
            case "NEAR" -> "NEAR Protocol";
            case "SHIB" -> "Shiba Inu";
            case "GRT" -> "The Graph";
            case "TON" -> "Toncoin";
            case "XRP" -> "XRP";
            default -> key;
        };
    }
}
