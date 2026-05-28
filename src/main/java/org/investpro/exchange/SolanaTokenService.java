package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides SPL token metadata, pricing and symbol normalisation.
 *
 * <p>Well-known tokens (SOL, USDC, USDT, etc.) are available offline via
 * {@link #WELL_KNOWN_TOKENS}. Dynamic metadata for unknown mints is fetched
 * from the Jupiter token list API (no API key required, public endpoint).
 */
public class SolanaTokenService {

    private static final Logger log = LoggerFactory.getLogger(SolanaTokenService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Jupiter public token list endpoint (no auth required). */
    private static final String JUPITER_TOKEN_LIST =
            "https://token.jup.ag/strict";

    /** CoinGecko free price API (no auth required for basic use). */
    private static final String COINGECKO_PRICE_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=%s&vs_currencies=usd";

    /**
     * Offline registry of well-known Solana tokens.
     * Key = mint address. Value = {@link SolanaTokenMetadata}.
     */
    public static final Map<String, SolanaTokenMetadata> WELL_KNOWN_TOKENS = Map.ofEntries(
            Map.entry("So11111111111111111111111111111111111111112",
                    new SolanaTokenMetadata("So11111111111111111111111111111111111111112",
                            "SOL", "Wrapped SOL", 9, "solana")),
            Map.entry("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                    new SolanaTokenMetadata("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                            "USDC", "USD Coin", 6, "usd-coin")),
            Map.entry("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
                    new SolanaTokenMetadata("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
                            "USDT", "Tether USD", 6, "tether")),
            Map.entry("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So",
                    new SolanaTokenMetadata("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So",
                            "mSOL", "Marinade Staked SOL", 9, "msol")),
            Map.entry("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs",
                    new SolanaTokenMetadata("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs",
                            "ETH", "Ether (Wormhole)", 8, "ethereum")),
            Map.entry("9n4nbM75f5Ui33ZbPYXn59EwSgE8CGsHtAeTH5YFeJ9E",
                    new SolanaTokenMetadata("9n4nbM75f5Ui33ZbPYXn59EwSgE8CGsHtAeTH5YFeJ9E",
                            "BTC", "Bitcoin (Wormhole)", 8, "bitcoin"))
    );

    private final HttpClient httpClient;

    public SolanaTokenService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // ── Offline lookup ────────────────────────────────────────────────────────

    /**
     * Returns metadata for a well-known token mint, or empty if not in the offline registry.
     *
     * @param mintAddress base-58 mint address
     * @return token metadata if known
     */
    public Optional<SolanaTokenMetadata> fetchTokenMetadata(String mintAddress) {
        return Optional.ofNullable(WELL_KNOWN_TOKENS.get(mintAddress));
    }

    /**
     * Normalises a token symbol to uppercase.
     *
     * @param rawSymbol raw symbol (e.g. "usdc", "sol")
     * @return normalised symbol (e.g. "USDC", "SOL")
     */
    public String normalizeTokenSymbol(String rawSymbol) {
        return rawSymbol == null ? "" : rawSymbol.strip().toUpperCase();
    }

    /**
     * Returns the list of well-known supported tokens (offline registry only).
     */
    public List<SolanaTokenMetadata> listSupportedTokens() {
        return new ArrayList<>(WELL_KNOWN_TOKENS.values());
    }

    // ── Dynamic metadata fetch ────────────────────────────────────────────────

    /**
     * Fetches token metadata from the Jupiter token list for an unknown mint.
     *
     * <p>Falls back to an empty optional on network or parse errors.
     *
     * @param mintAddress base-58 mint address
     * @return future resolving to the token metadata, or empty if not found
     */
    public CompletableFuture<Optional<SolanaTokenMetadata>> fetchTokenMetadataDynamic(
            String mintAddress) {

        // First check offline registry
        Optional<SolanaTokenMetadata> offline = fetchTokenMetadata(mintAddress);
        if (offline.isPresent()) {
            return CompletableFuture.completedFuture(offline);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JUPITER_TOKEN_LIST))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) return Optional.<SolanaTokenMetadata>empty();
                    try {
                        JsonNode arr = MAPPER.readTree(response.body());
                        if (arr.isArray()) {
                            for (JsonNode token : arr) {
                                if (mintAddress.equals(token.path("address").asText())) {
                                    return Optional.of(new SolanaTokenMetadata(
                                            mintAddress,
                                            token.path("symbol").asText(""),
                                            token.path("name").asText(""),
                                            token.path("decimals").asInt(0),
                                            ""));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Solana: failed to fetch Jupiter token list: {}", e.getMessage());
                    }
                    return Optional.<SolanaTokenMetadata>empty();
                });
    }

    // ── Price fetch ───────────────────────────────────────────────────────────

    /**
     * Fetches the USD price for a token via CoinGecko (free tier, no key required).
     *
     * <p>Uses the CoinGecko ID stored in {@link SolanaTokenMetadata#coingeckoId()}.
     *
     * @param metadata token metadata containing the CoinGecko ID
     * @return USD price, or {@link BigDecimal#ZERO} if unavailable
     */
    public CompletableFuture<BigDecimal> fetchTokenPrice(SolanaTokenMetadata metadata) {
        if (metadata.coingeckoId() == null || metadata.coingeckoId().isBlank()) {
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }

        String url = COINGECKO_PRICE_URL.formatted(metadata.coingeckoId());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) return BigDecimal.ZERO;
                    try {
                        JsonNode root = MAPPER.readTree(response.body());
                        double  price = root.path(metadata.coingeckoId()).path("usd").asDouble(0);
                        return BigDecimal.valueOf(price);
                    } catch (Exception e) {
                        log.debug("Solana: failed to parse CoinGecko price for {}: {}",
                                metadata.symbol(), e.getMessage());
                        return BigDecimal.ZERO;
                    }
                })
                .exceptionally(ex -> {
                    log.debug("Solana: price fetch error for {}: {}", metadata.symbol(), ex.getMessage());
                    return BigDecimal.ZERO;
                });
    }

    // ── Data records ──────────────────────────────────────────────────────────

    /**
     * Immutable SPL token metadata.
     *
     * @param mint         base-58 mint address
     * @param symbol       normalised ticker symbol (e.g. USDC)
     * @param name         full token name (e.g. USD Coin)
     * @param decimals     number of decimal places
     * @param coingeckoId  CoinGecko coin ID for price lookups (may be empty)
     */
    public record SolanaTokenMetadata(
            String mint,
            String symbol,
            String name,
            int decimals,
            String coingeckoId
    ) {}
}
