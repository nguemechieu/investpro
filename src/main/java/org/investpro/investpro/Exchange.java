package org.investpro.investpro;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.model.CoinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
        MetadataProvider {

    public static final Logger logger = LoggerFactory.getLogger(Exchange.class);
    protected static final long CACHE_EXPIRY_MS = 1000;
    protected static final int MAX_RETRIES = 5;
    protected static String apiSecret;
    protected final String apiKey;
    private final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    HttpClient.Builder client = HttpClient.newBuilder();
    private List<News> cachedNews = new ArrayList<>();
    private long lastNewsFetchTime = 0;


    protected Exchange(String apiKey, String apiSecret) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        Exchange.apiSecret = Objects.requireNonNull(apiSecret, "API secret must not be null");
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
            HttpResponse<String> response = client.build().send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                logger.info("Rate limit hit");
                return new ArrayList<>();
            }
            if (response.statusCode() != 200) {
                logger.info("Failed to fetch news: " + response.statusCode());
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
        try {
            requestBuilder.uri(URI.create(
                    "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false"
            ));

            HttpResponse<String> response = client.build().send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });

        } catch (IOException | InterruptedException e) {
            logger.error("\u274C Failed to fetch coin info: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    protected <T> List<T> fetchWithRetries(FetchFunction<List<T>> function) {
        int retries = 0;
        int delay = 1000;

        while (retries < MAX_RETRIES) {
            try {
                return function.fetch();
            } catch (Exception e) {
                retries++;
                logger.warn("Retry {}/{} failed: {}", retries, MAX_RETRIES, e.getMessage());

                try {

                    Thread.sleep(delay);
                    if (retries == MAX_RETRIES) {
                        logger.info("❌ Failed after retries", e);
                        return new ArrayList<>();
                    }
                } catch (InterruptedException ignored) {
                }
                delay *= 2;
            }
        }
        return Collections.emptyList();
    }

    @FunctionalInterface
    protected interface FetchFunction<T> {
        T fetch() throws Exception;
    }
}
