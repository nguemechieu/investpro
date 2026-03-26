package org.investpro.investpro;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.model.CoinInfo;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base for HTTP-only Exchange implementations.
 * Includes high-level operations with optional caching and retry mechanisms.
 */
public abstract class Exchange implements
        TradeOperations,
        MarketDataProvider,
        AccountProvider,
        NewsService,
        CandleService,
        TradeService,
        MetadataProvider {

    public static final Logger logger = LoggerFactory.getLogger(Exchange.class);
    protected static final long CACHE_EXPIRY_MS = 1000;
    protected static final int MAX_RETRIES = 5;
    protected final String apiSecret;
    protected final String apiKey;
    private static final long COIN_INFO_CACHE_MS = 60_000;
    private static final long COIN_INFO_RATE_LIMIT_COOLDOWN_MS = 180_000;
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private List<News> cachedNews = new ArrayList<>();
    private long lastNewsFetchTime = 0;
    private List<CoinInfo> cachedCoinInfo = new ArrayList<>();
    private long lastCoinInfoFetchTime = 0;
    private long coinInfoCooldownUntil = 0;

    protected Exchange(String apiKey, String apiSecret) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        this.apiSecret = Objects.requireNonNull(apiSecret, "API secret must not be null");
        logger.info("Exchange initialized securely.");
    }

    @Override
    public List<News> getLatestNews() {
        long now = System.currentTimeMillis();
        if (!cachedNews.isEmpty() && (now - lastNewsFetchTime) < CACHE_EXPIRY_MS) {
            return cachedNews;
        }
        List<News> newsList = fetchWithRetries(() -> {
            URI uri = URI.create("https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=315414327b0217e69b09c39132fe08d8");
            requestBuilder.uri(uri);
            HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                logger.info("Rate limit hit");
                return new ArrayList<>();
            }
            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch news: status={}, body={}", response.statusCode(), response.body());
                return new ArrayList<>();
            }
            ObjectMapper objectMapper = new ObjectMapper();
            List<News> parsedNews = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                for (JsonNode node : root) {
                    parsedNews.add(objectMapper.convertValue(node, News.class));
                }
            }
            return parsedNews;
        });

        if (newsList != null) {
            cachedNews = newsList;
            lastNewsFetchTime = now;
        }
        return cachedNews;
    }

    @Override
    public List<CoinInfo> getCoinInfoList() {
        long now = System.currentTimeMillis();
        if (!cachedCoinInfo.isEmpty() && (now - lastCoinInfoFetchTime) < COIN_INFO_CACHE_MS) {
            return cachedCoinInfo;
        }
        if (coinInfoCooldownUntil > now) {
            return cachedCoinInfo;
        }

        try {
            requestBuilder.uri(URI.create(
                    "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false"
            ));

            HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                coinInfoCooldownUntil = now + COIN_INFO_RATE_LIMIT_COOLDOWN_MS;
                logger.warn("CoinGecko rate limit hit. Reusing cached coin info for {} seconds.", COIN_INFO_RATE_LIMIT_COOLDOWN_MS / 1000);
                return cachedCoinInfo;
            }
            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch coin info: status={}, body={}", response.statusCode(), response.body());
                return cachedCoinInfo;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            List<CoinInfo> latest = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            cachedCoinInfo = latest;
            lastCoinInfoFetchTime = now;
            coinInfoCooldownUntil = 0;
            return latest;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Failed to fetch coin info", e);
            return cachedCoinInfo;
        }
    }

    protected <T> List<T> fetchWithRetries(FetchFunction<List<T>> function) {
        int attempts = 0;
        long delay = 1000;

        while (attempts < MAX_RETRIES) {
            try {
                return function.fetch();
            } catch (Exception e) {
                logger.warn("Attempt {}/{} failed: {}", attempts + 1, MAX_RETRIES, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry sleep interrupted", ie);
                    break;
                }
                delay *= 2;
                attempts++;
            }
        }
        logger.error("All {} retries failed for fetch operation", MAX_RETRIES);
        return Collections.emptyList();
    }

    public abstract Set<Integer> granularity();

    public abstract CompletableFuture<List<Trade>> fetchRecentTrades(TradePair tradePair, Instant instant);

    public abstract CompletableFuture<List<OrderBook>> getOrderBook(TradePair tradePair, Instant instant);

    @FunctionalInterface
    protected interface FetchFunction<T> {
        T fetch() throws Exception;
    }
}
