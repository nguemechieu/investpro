package org.investpro;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.investpro.exchanges.Coinbase.client;
import static org.investpro.exchanges.Coinbase.requestBuilder;


/**
 * An abstract base class for {@code Exchange} implementations without WebSocket.
 * Provides common methods and behaviors for HTTP-based communication.
 */
public abstract class Exchange {

    protected static final Logger logger = LoggerFactory.getLogger(
            Exchange.class
    );
    public CandleStickChart.UpdateInProgressCandleTask updateInProgressCandleTask;

    protected String apiKey;
    protected static String apiSecret;
    public LiveTradesConsumer liveTradesConsumer;
    protected String message;

    /**
     * Constructor with API key and secret.
     */
    protected Exchange(String apiKey, String apiSecret) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        Exchange.apiSecret = Objects.requireNonNull(apiSecret, "API secret must not be null");
        logger.info("Exchange initialized with API key and secret.");
    }

    public abstract List<Fee> getTradingFee() throws IOException, InterruptedException;

    /**
     * Fetch user accounts asynchronously.
     */
    public abstract List<Account> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;


    /**
     * Create an order on the exchange.
     */
    public abstract void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType,
                                     double price, double size, Date timestamp, double stopLoss, double takeProfit)
            throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;

    /**
     * Cancel an order on the exchange.
     */
    public abstract void cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    public abstract CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair);

    /**
     * Get exchange message or status.
     */
    public abstract String getExchangeMessage();


    /**
     * Get the supplier for candle data (e.g., candlestick chart data).
     */
    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    private final long CACHE_EXPIRY = TimeUnit.MINUTES.toMillis(5);  // Cache for 5 minutes

    /**
     * Fetch candle data for the current in-progress candle, used for live syncing.
     */
    public abstract CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);


    /**
     * Retrieve the list of pending orders.
     */
    public abstract List<Order> getPendingOrders() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException;

    public abstract List<Position> getPositions() throws IOException, InterruptedException, ExecutionException;

    /**
     * Retrieve open orders for a specific trade pair.
     */
    public abstract List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException;

    /**
     * Retrieve a list of orders from the exchange.
     */
    public abstract List<Order> getOrders() throws IOException, InterruptedException, SQLException, ClassNotFoundException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;

    /**
     * Retrieve the available trade pairs from the exchange.
     */
    public abstract List<TradePair> getTradePairs() throws Exception;

    /**
     * Clear live trades consumer data.
     */
    public void clear() {
        liveTradesConsumer.clear();
    }

    /**
     * Add an exchange to the live trades consumer.
     */



    public abstract void stopStreamLiveTrades(@NotNull TradePair tradePair);

    // Get candlestick data
    // WebSocket client for live updates
    // Stream live prices
    public abstract List<PriceData> streamLivePrices(@NotNull TradePair symbol);

    // Stream live candlestick data
    public abstract List<CandleData> streamLiveCandlestick(@NotNull TradePair symbol, int intervalSeconds);


    // Cancel all orders
    public abstract void cancelAllOrders() throws InvalidKeyException, NoSuchAlgorithmException, IOException;

    public abstract boolean supportsStreamingTrades(TradePair tradePair);


    public abstract List<Deposit> Deposit() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    public abstract List<Withdrawal> Withdraw() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    public abstract List<Trade> getLiveTrades(List<TradePair> tradePairs);
    private List<News> cachedNews = new ArrayList<>();

    public abstract CustomWebSocketClient getWebsocketClient(Exchange exchange,TradePair tradePair, int secondsPerCandles);
    private long lastFetchTime = 0;

    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(Exchange exchange,TradePair tradePair, Instant stopAt, int secondsPerCandle, Consumer<List<Trade>> tradeConsumer);

    public abstract double fetchLivesBidAsk(TradePair tradePair);

    public abstract List<Account> getAccountSummary();

    public List<News> getLatestNews() {

        cachedNews.add(new News());
        long currentTime = System.currentTimeMillis();

        // ✅ Return cached news if the last fetch was recent
        if (!cachedNews.isEmpty() && (currentTime - lastFetchTime) < CACHE_EXPIRY) {
            return cachedNews;
        }

        List<News> newsList = new ArrayList<>();
        int retries = 0;
        int maxRetries = 5;
        int delay = 1000; // Initial delay of 1 sec

        while (retries < maxRetries) {
            try {
                URI uri = URI.create("https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=315414327b0217e69b09c39132fe08d8");
                requestBuilder.uri(uri);

                // 🌍 Send HTTP request
                HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                // 🔄 Handle Rate Limit (HTTP 429)
                if (response.statusCode() == 429) {
                    retries++;
                    logger.error("⚠️ Rate limit hit! Retrying in {} ms...", delay);
                    Thread.sleep(delay);
                    delay *= 2; // Exponential backoff
                    continue;
                }

                // ✅ Validate successful response
                if (response.statusCode() != 200) {
                    throw new RuntimeException("❌ Failed to fetch news: " + response.statusCode() + " - " + response.body());
                }

                // 📌 Parse JSON response
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(response.body());

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        News news = objectMapper.convertValue(node, News.class);  // ✅ Correct JSON mapping
                        logger.info("News: {}", news);
                        newsList.add(news);
                    }
                }

                // ✅ Update cache & return result
                cachedNews = new ArrayList<>(newsList);
                lastFetchTime = System.currentTimeMillis();
                return newsList;

            } catch (IOException | InterruptedException e) {
                retries++;
                if (retries == maxRetries) {
                    throw new RuntimeException("❌ Error fetching news after retries: ", e);
                }
                logger.error("\uD83D\uDD04 Retrying due to error: {}", e.getMessage());
            }
        }

        return newsList; // If all retries fail, return empty list
    }

    public List<CoinInfo> getCoinInfoList() {
        requestBuilder.uri(
                URI.create(
                        "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false"
                )
        );
        try {
            HttpResponse<String> response = client.send(
                    requestBuilder.GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public abstract Set<Integer> getSupportedGranularity();
}