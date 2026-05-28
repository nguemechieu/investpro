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
 * Loads currency flag/icon images for display in market watch tables.
 * <p>
 * Fiat and crypto images are fully separated in both storage and download logic:
 * <ul>
 *   <li><b>Crypto</b>: CoinGecko API → {@code ~/.investpro/currency-icons/crypto/}</li>
 *   <li><b>Fiat</b>: flagcdn.com (country flags) → {@code ~/.investpro/currency-icons/fiat/}</li>
 * </ul>
 * <p>
 * Lookup order for each type:
 * <ol>
 *   <li>Bundled classpath icons (type-specific paths)</li>
 *   <li>Locally cached downloaded icons (type-specific subdirectory)</li>
 *   <li>Remote download — flagcdn.com for fiat, CoinGecko for crypto</li>
 *   <li>Generic fallback image</li>
 * </ol>
 */
@Slf4j
public final class CurrencyIconLoader {

    // --- Crypto: symbol → CoinGecko ID ---
    private static final Map<String, String> CRYPTO_COINGECKO_ALIASES = Map.ofEntries(
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

    /**
     * Fiat ISO 4217 currency code → ISO 3166-1 alpha-2 country code for flagcdn.com.
     * URL pattern: https://flagcdn.com/w80/{countryCode}.png
     */
    private static final Map<String, String> FIAT_TO_COUNTRY_CODE = Map.ofEntries(
            Map.entry("USD", "us"),
            Map.entry("EUR", "eu"),
            Map.entry("GBP", "gb"),
            Map.entry("JPY", "jp"),
            Map.entry("CHF", "ch"),
            Map.entry("CAD", "ca"),
            Map.entry("AUD", "au"),
            Map.entry("NZD", "nz"),
            Map.entry("CNY", "cn"),
            Map.entry("CNH", "cn"),
            Map.entry("HKD", "hk"),
            Map.entry("SGD", "sg"),
            Map.entry("ZAR", "za"),
            Map.entry("MXN", "mx"),
            Map.entry("BRL", "br"),
            Map.entry("INR", "in"),
            Map.entry("KRW", "kr"),
            Map.entry("TRY", "tr"),
            Map.entry("SEK", "se"),
            Map.entry("NOK", "no"),
            Map.entry("DKK", "dk"),
            Map.entry("PLN", "pl"),
            Map.entry("HUF", "hu"),
            Map.entry("CZK", "cz"),
            Map.entry("ILS", "il"),
            Map.entry("THB", "th"),
            Map.entry("MYR", "my"),
            Map.entry("IDR", "id"),
            Map.entry("PHP", "ph"),
            Map.entry("TWD", "tw"),
            Map.entry("SAR", "sa"),
            Map.entry("AED", "ae"),
            Map.entry("KWD", "kw"),
            Map.entry("QAR", "qa"),
            Map.entry("EGP", "eg"),
            Map.entry("NGN", "ng"),
            Map.entry("CLP", "cl"),
            Map.entry("COP", "co"),
            Map.entry("PEN", "pe"),
            Map.entry("ARS", "ar"),
            Map.entry("VND", "vn"),
            Map.entry("UAH", "ua"),
            Map.entry("RON", "ro"),
            Map.entry("BGN", "bg"),
            Map.entry("HRK", "hr"),
            Map.entry("RUB", "ru"),
            Map.entry("PKR", "pk"),
            Map.entry("MAD", "ma"),
            Map.entry("DZD", "dz"),
            Map.entry("KES", "ke"));

    private static final String FALLBACK_PATH = "/icons/currencies/unknown.png";
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3";
    /** flagcdn.com: free, no API key, 2-letter ISO 3166-1 alpha-2 country code, 80px wide PNG. */
    private static final String FLAGCDN_URL = "https://flagcdn.com/w80/%s.png";
    private static final long HTTP_TIMEOUT_SECONDS = 5;

    /** Separate local disk cache subdirectories for fiat and crypto images. */
    private static final Path LOCAL_CACHE_ROOT = Path.of(
            System.getProperty("user.home", "."), ".investpro", "currency-icons");
    private static final Path FIAT_CACHE_DIR = LOCAL_CACHE_ROOT.resolve("fiat");
    private static final Path CRYPTO_CACHE_DIR = LOCAL_CACHE_ROOT.resolve("crypto");

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> COINGECKO_ID_CACHE = new ConcurrentHashMap<>();
    private static volatile Image fallbackImage;
    private static volatile OkHttpClient httpClient;
    private static volatile ObjectMapper objectMapper;

    private CurrencyIconLoader() {
    }

    /**
     * Load the icon for the given currency code (e.g. "BTC", "USD", "EUR").
     * Fiat and crypto currencies are resolved via fully separate paths and providers.
     *
     * @param currencyCode ISO 4217 fiat code or crypto symbol
     * @return Image, or fallback if not found
     */
    public static Image loadCurrencyIcon(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return loadFallback();
        }

        String code = currencyCode.trim().toUpperCase();
        return CACHE.computeIfAbsent(code, key -> {
            boolean fiat = isFiatCurrency(key);

            // 1. Bundled classpath icons (type-specific paths)
            Image icon = candidatesFor(key, fiat).stream()
                    .map(CurrencyIconLoader::tryLoadFromResources)
                    .filter(img -> img != null && !img.isError())
                    .findFirst()
                    .orElse(null);
            if (icon != null) return icon;

            // 2. Locally cached downloaded icons (type-specific subdirectory)
            icon = tryLoadFromLocalCache(key, fiat);
            if (icon != null && !icon.isError()) return icon;

            // 3. Remote download — flagcdn.com for fiat, CoinGecko for crypto
            if (fiat) {
                icon = tryDownloadFiatFlag(key);
            } else {
                icon = tryLoadFromCoinGecko(key);
            }
            if (icon != null && !icon.isError()) return icon;

            log.debug("Currency icon not found for {} ({}), using fallback.", key, fiat ? "fiat" : "crypto");
            return loadFallback();
        });
    }

    // ---------- Fiat detection ----------

    /**
     * Returns true if the code represents a fiat/forex currency.
     * Checks the explicit fiat map first, then falls back to java.util.Currency recognition.
     */
    static boolean isFiatCurrency(String code) {
        if (code == null || code.isBlank()) return false;
        if (FIAT_TO_COUNTRY_CODE.containsKey(code)) return true;
        try {
            java.util.Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    // ---------- Classpath candidate paths (type-specific) ----------

    private static List<String> candidatesFor(String key, boolean fiat) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (fiat) {
            return List.of(
                    "/icons/fiat/" + lower + ".png",
                    "/icons/fiat/" + lower + ".svg",
                    "/icons/currencies/" + lower + ".png",
                    "/currency/fiat/" + lower + ".png");
        } else {
            String alias = CRYPTO_COINGECKO_ALIASES.getOrDefault(key, lower);
            return List.of(
                    "/icons/crypto/" + lower + ".png",
                    "/icons/crypto/" + alias + ".png",
                    "/currency/" + alias + ".jpg",
                    "/currency/" + lower + ".jpg",
                    "/currency/" + prettify(key) + ".jpg",
                    "/icons/currencies/" + lower + ".png");
        }
    }

    // ---------- Local disk cache (fiat/ vs crypto/ subdirectory) ----------

    private static Image tryLoadFromLocalCache(String currencyCode, boolean fiat) {
        Path path = localIconPath(currencyCode, fiat);
        if (!Files.isRegularFile(path)) return null;
        try (InputStream stream = Files.newInputStream(path)) {
            Image image = new Image(stream, 100, 100, true, true);
            if (image.isError()) {
                log.debug("Cached currency icon is invalid for {} at {}", currencyCode, path);
                return null;
            }
            return image;
        } catch (IOException ex) {
            log.debug("Failed to load cached currency icon for {} from {}: {}",
                    currencyCode, path, ex.getMessage());
            return null;
        }
    }

    private static void saveToLocalCache(String currencyCode, byte[] bytes, boolean fiat) {
        try {
            Path dir = fiat ? FIAT_CACHE_DIR : CRYPTO_CACHE_DIR;
            Files.createDirectories(dir);
            Files.write(localIconPath(currencyCode, fiat), bytes);
            log.debug("Saved {} icon for {} to local cache", fiat ? "fiat" : "crypto", currencyCode);
        } catch (IOException ex) {
            log.debug("Failed to cache currency icon for {}: {}", currencyCode, ex.getMessage());
        }
    }

    private static Path localIconPath(String currencyCode, boolean fiat) {
        String safe = (currencyCode == null ? "unknown" : currencyCode.trim().toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9._-]", "_");
        return (fiat ? FIAT_CACHE_DIR : CRYPTO_CACHE_DIR).resolve(safe + ".png");
    }

    // ---------- Fiat download: flagcdn.com ----------

    /**
     * Downloads a country flag from flagcdn.com for the given fiat currency code.
     * Uses the explicit {@link #FIAT_TO_COUNTRY_CODE} map to resolve ISO 3166-1 alpha-2 country code.
     * Free service, no API key required.
     */
    private static Image tryDownloadFiatFlag(String currencyCode) {
        String countryCode = FIAT_TO_COUNTRY_CODE.get(currencyCode);
        if (countryCode == null || countryCode.length() != 2) {
            log.debug("No country code mapping for fiat currency: {}; cannot download flag.", currencyCode);
            return null;
        }
        String url = FLAGCDN_URL.formatted(countryCode);
        log.debug("Downloading fiat flag for {} (country={}) from {}", currencyCode, countryCode, url);
        return downloadAndCacheImage(currencyCode, url, true);
    }

    // ---------- Crypto download: CoinGecko ----------

    private static Image tryLoadFromCoinGecko(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return null;
        try {
            String coinGeckoId = getCoinGeckoId(currencyCode);
            if (coinGeckoId == null || coinGeckoId.isBlank()) return null;

            String infoUrl = "%s/coins/%s?localization=false".formatted(COINGECKO_API, coinGeckoId);
            String body = fetchUrlContent(infoUrl);
            if (body == null) return null;

            JsonNode root = getObjectMapper().readTree(body);
            String imageUrl = root.path("image").path("large").asText("");
            if (imageUrl.isBlank()) return null;

            return downloadAndCacheImage(currencyCode, imageUrl, false);
        } catch (Exception ex) {
            log.debug("Failed to fetch icon from CoinGecko for {}: {}", currencyCode, ex.getMessage());
            return null;
        }
    }

    private static String getCoinGeckoId(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;

        String cached = COINGECKO_ID_CACHE.get(symbol);
        if (cached != null) return cached;

        String alias = CRYPTO_COINGECKO_ALIASES.get(symbol);
        if (alias != null) {
            COINGECKO_ID_CACHE.put(symbol, alias);
            return alias;
        }

        try {
            String searchUrl = "%s/search?query=%s".formatted(COINGECKO_API, symbol.toLowerCase());
            String body = fetchUrlContent(searchUrl);
            if (body == null) return null;

            JsonNode root = getObjectMapper().readTree(body);
            JsonNode coins = root.path("coins");
            if (coins.isArray() && !coins.isEmpty()) {
                String coinId = coins.get(0).path("id").asText("");
                if (!coinId.isBlank()) {
                    COINGECKO_ID_CACHE.put(symbol, coinId);
                    return coinId;
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to find CoinGecko ID for {}: {}", symbol, ex.getMessage());
        }
        return null;
    }

    // ---------- Shared HTTP helpers ----------

    private static Image downloadAndCacheImage(String currencyCode, String imageUrl, boolean fiat) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        try {
            OkHttpClient client = getHttpClient();
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .header("User-Agent", "InvestPro/1.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                byte[] bytes = response.body().bytes();
                if (bytes.length == 0) return null;
                Image image = new Image(new ByteArrayInputStream(bytes), 100, 100, true, true);
                if (image.isError()) return null;
                saveToLocalCache(currencyCode, bytes, fiat);
                return image;
            }
        } catch (Exception ex) {
            log.debug("Failed to download currency icon from {}: {}", imageUrl, ex.getMessage());
            return null;
        }
    }

    private static String fetchUrlContent(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            OkHttpClient client = getHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "InvestPro/1.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                return response.body().string();
            }
        } catch (Exception ex) {
            log.debug("Failed to fetch URL {}: {}", url, ex.getMessage());
            return null;
        }
    }

    // ---------- Classpath resource loader and fallback ----------

    private static Image tryLoadFromResources(String resourcePath) {
        try (InputStream stream = CurrencyIconLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) return null;
            Image image = new Image(stream);
            return image.isError() ? null : image;
        } catch (Exception ex) {
            log.debug("Failed to load icon from {}: {}", resourcePath, ex.getMessage());
            return null;
        }
    }

    private static Image loadFallback() {
        Image image = fallbackImage;
        if (image == null) {
            image = tryLoadFromResources(FALLBACK_PATH);
            fallbackImage = image;
        }
        return image;
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

    // ---------- Lazy singletons ----------

    private static OkHttpClient getHttpClient() {
        OkHttpClient client = httpClient;
        if (client == null) {
            synchronized (CurrencyIconLoader.class) {
                client = httpClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
