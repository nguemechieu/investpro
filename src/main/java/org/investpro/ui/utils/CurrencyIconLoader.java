package org.investpro.ui.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Loads currency flag / icon images for display in market watch tables.
 * <p>
 * Lookup order:
 * 1. Bundled icons from {@code /currency} or {@code /icons/currencies}
 * 2. Locally cached downloaded icons
 * 3. CoinGecko API for crypto currencies (saved locally after download)
 * 4. java.util.Currency flag fallback for fiat currencies
 * 5. Generic fallback image
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
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3";
    private static final long COINGECKO_TIMEOUT_SECONDS = 5;
    private static final Path LOCAL_ICON_CACHE_DIR = Path.of(
            System.getProperty("user.home", "."),
            ".investpro",
            "currency-icons");

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> COINGECKO_ID_CACHE = new ConcurrentHashMap<>();
    private static volatile Image fallbackImage;
    private static volatile OkHttpClient httpClient;
    private static volatile ObjectMapper objectMapper;

    private CurrencyIconLoader() {
    }

    /**
     * Load the icon for the given currency code (e.g. "BTC", "USD").
     * Tries bundled icons first, then CoinGecko for crypto, then java.util.Currency for fiat.
     *
     * @param currencyCode ISO or crypto currency code
     * @return Image, or fallback if not found
     */
    public static Image loadCurrencyIcon(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return loadFallback();
        }

        String code = currencyCode.trim().toUpperCase();
        return CACHE.computeIfAbsent(code, key -> {
            // Try 1: Load from bundled resources
            Image icon = candidates(key).stream()
                    .map(CurrencyIconLoader::tryLoadFromResources)
                    .filter(image -> image != null && !image.isError())
                    .findFirst()
                    .orElse(null);
            if (icon != null) {
                return icon;
            }

            // Try 2: Load previously downloaded icon from the local cache
            icon = tryLoadFromLocalCache(key);
            if (icon != null && !icon.isError()) {
                return icon;
            }

            // Try 3: Fetch from CoinGecko if crypto, then save locally for future runs
            icon = tryLoadFromCoinGecko(key);
            if (icon != null && !icon.isError()) {
                return icon;
            }

            // Try 4: Fetch fiat currency flag from java.util.Currency
            icon = tryLoadFiatCurrencyIcon(key);
            if (icon != null && !icon.isError()) {
                return icon;
            }

            log.debug("Currency icon not found for {} from any source, using fallback.", key);
            return loadFallback();
        });
    }

    /**
     * Try to load icon from CoinGecko API for crypto currencies.
     */
    private static Image tryLoadFromCoinGecko(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return null;
        }

        try {
            String coinGeckoId = getCoinGeckoId(currencyCode);
            if (coinGeckoId == null || coinGeckoId.isBlank()) {
                return null;
            }

            String iconUrl = "%s/coins/%s?localization=false".formatted(COINGECKO_API, coinGeckoId);
            String responseBody = fetchUrlContent(iconUrl);

            if (responseBody == null) {
                return null;
            }

            JsonNode root = getObjectMapper().readTree(responseBody);
            String imageUrl = root.path("image").path("large").asText("");

            if (imageUrl.isBlank()) {
                return null;
            }

            return downloadAndCacheImage(currencyCode, imageUrl);

        } catch (Exception e) {
            log.debug("Failed to fetch icon from CoinGecko for {}: {}", currencyCode, e.getMessage());
            return null;
        }
    }

    /**
     * Try to load fiat currency icon using java.util.Currency locale display icons.
     * Attempts to load country flag emoji or specialized fiat icon.
     */
    private static Image tryLoadFiatCurrencyIcon(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return null;
        }

        try {
            // Check if java.util.Currency recognizes this code
            java.util.Currency javaCurrency = java.util.Currency.getInstance(currencyCode);
            if (javaCurrency == null) {
                return null;
            }

            // Try to load from fiat currency icon directory
            return tryLoadFromResources("/currency/fiat/" + currencyCode.toLowerCase() + ".png");

        } catch (IllegalArgumentException e) {
            // Code is not a valid ISO 4217 currency code
            return null;
        } catch (Exception e) {
            log.debug("Failed to load fiat currency icon for {}: {}", currencyCode, e.getMessage());
            return null;
        }
    }

    /**
     * Get CoinGecko coin ID for a given crypto symbol.
     * Checks cache first, then queries API.
     */
    private static String getCoinGeckoId(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        String cached = COINGECKO_ID_CACHE.get(symbol);
        if (cached != null) {
            return cached;
        }

        // Check if symbol is in our aliases
        String alias = SYMBOL_ALIASES.get(symbol);
        if (alias != null) {
            COINGECKO_ID_CACHE.put(symbol, alias);
            return alias;
        }

        // Query CoinGecko for the symbol
        try {
            String searchUrl = "%s/search?query=%s".formatted(COINGECKO_API, symbol.toLowerCase());
            String responseBody = fetchUrlContent(searchUrl);

            if (responseBody == null) {
                return null;
            }

            JsonNode root = getObjectMapper().readTree(responseBody);
            JsonNode coins = root.path("coins");

            if (coins.isArray() && coins.size() > 0) {
                String coinId = coins.get(0).path("id").asText("");
                if (!coinId.isBlank()) {
                    COINGECKO_ID_CACHE.put(symbol, coinId);
                    return coinId;
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("Failed to find CoinGecko ID for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch URL content via HTTP using OkHttp.
     */
    private static String fetchUrlContent(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            OkHttpClient client = getHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "InvestPro/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                return response.body().string();
            }

        } catch (Exception e) {
            log.debug("Failed to fetch URL {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static Image tryLoadFromLocalCache(String currencyCode) {
        Path path = localIconPath(currencyCode);
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (InputStream stream = Files.newInputStream(path)) {
            Image image = new Image(stream, 100, 100, true, true);
            if (image.isError()) {
                log.debug("Cached currency icon is invalid for {} at {}", currencyCode, path);
                return null;
            }
            return image;
        } catch (IOException exception) {
            log.debug("Failed to load cached currency icon for {} from {}: {}",
                    currencyCode, path, exception.getMessage());
            return null;
        }
    }

    private static Image downloadAndCacheImage(String currencyCode, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        try {
            OkHttpClient client = getHttpClient();
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .header("User-Agent", "InvestPro/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }

                byte[] imageBytes = response.body().bytes();
                if (imageBytes.length == 0) {
                    return null;
                }

                Image image = new Image(new ByteArrayInputStream(imageBytes), 100, 100, true, true);
                if (image.isError()) {
                    return null;
                }

                saveToLocalCache(currencyCode, imageBytes);
                return image;
            }
        } catch (Exception exception) {
            log.debug("Failed to download currency icon from URL {}: {}", imageUrl, exception.getMessage());
            return null;
        }
    }

    private static void saveToLocalCache(String currencyCode, byte[] imageBytes) {
        try {
            Files.createDirectories(LOCAL_ICON_CACHE_DIR);
            Files.write(localIconPath(currencyCode), imageBytes);
        } catch (IOException exception) {
            log.debug("Failed to cache currency icon for {}: {}", currencyCode, exception.getMessage());
        }
    }

    private static Path localIconPath(String currencyCode) {
        String safeCode = currencyCode == null ? "unknown" : currencyCode.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return LOCAL_ICON_CACHE_DIR.resolve(safeCode + ".png");
    }

    private static Image loadFallback() {
        Image image = fallbackImage;
        if (image == null) {
            image = tryLoadFromResources(FALLBACK_PATH);
            fallbackImage = image;
        }
        return image;
    }

    private static Image tryLoadFromResources(String resourcePath) {
        try {
            try (InputStream stream = CurrencyIconLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return new Image(stream);
            }
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

    private static OkHttpClient getHttpClient() {
        OkHttpClient client = httpClient;
        if (client == null) {
            synchronized (CurrencyIconLoader.class) {
                client = httpClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(COINGECKO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(COINGECKO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .build();
                    httpClient = client;
                }
            }
        }
        return client;
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = objectMapper;
        if (mapper == null) {
            synchronized (CurrencyIconLoader.class) {
                mapper = objectMapper;
                if (mapper == null) {
                    mapper = new ObjectMapper();
                    objectMapper = mapper;
                }
            }
        }
        return mapper;
    }
}
