package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NewsDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(NewsDataProvider.class);
    private static final String NEWS_API_URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823";
    // Fetch news every 10 minutes
    private static final long FETCH_INTERVAL = 10; // in minutes
    private final HttpClient client;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ArrayList<News> newsList = new ArrayList<>();

    public NewsDataProvider() {
        // Initialize HTTP client
        this.client = HttpClient.newHttpClient();

        // Schedule periodic fetching of news data
        scheduler.scheduleAtFixedRate(
                () -> fetchNewsData()
                        .thenAccept(news -> {
                            try {
                                this.newsList = parseNews(news);
                                logger.info("News data successfully parsed and loaded.");
                            } catch (ParseException e) {
                                logger.error("Error parsing news data: {}", e.getMessage());
                            }
                        })
                        .exceptionally(e -> {
                            logger.error("Error fetching news data: {}", e.getMessage());
                            return null;
                        }),
                0, FETCH_INTERVAL, TimeUnit.MINUTES
        );
        shutdownScheduler();

    }

    /**
     * Converts a date string into a Date object.
     *
     * @param dateStr The date string in ISO 8601 format.
     * @return The corresponding Date object.
     * @throws ParseException If the date string cannot be parsed.
     */
    @Contract("null -> fail")
    private static Date convertStringToDate(String dateStr) throws ParseException {
        if (dateStr == null) throw new IllegalArgumentException("Invalid date string");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return dateFormat.parse(dateStr);
    }

    /**
     * Fetches news data from the provided URL asynchronously.
     *
     * @return A CompletableFuture containing the response body as a String.
     */
    private CompletableFuture<String> fetchNewsData() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NewsDataProvider.NEWS_API_URL))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .completeOnTimeout("REQUEST TIMEOUT", 5000, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    logger.error("Error during HTTP request: {}", throwable.getMessage());
                    return null;
                });
    }

    /**
     * Parses the news data from the JSON string response.
     *
     * @param jsonResponse The JSON response containing news data.
     * @return A list of parsed News objects.
     * @throws ParseException If the date parsing fails.
     */
    private @NotNull ArrayList<News> parseNews(String jsonResponse) throws ParseException {
        JSONArray jsonArray = new JSONArray(jsonResponse);
        ArrayList<News> parsedNewsList = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            News news = new News(
                    jsonObject.getString("title"),
                    jsonObject.getString("country"),
                    jsonObject.getString("impact"),
                    convertStringToDate(jsonObject.getString("date")),
                    jsonObject.optString("forecast", String.valueOf(0)),
                    jsonObject.optString("previous", String.valueOf(0))
            );
            parsedNewsList.add(news);
        }

        return parsedNewsList;
    }

    /**
     * Returns the list of news items.
     *
     * @return A list of news items.
     */
    public List<News> getNewsList() {
        return newsList;
    }

    /**
     * Shuts down the scheduler gracefully.
     */
    public void shutdownScheduler() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            logger.error("Scheduler shutdown interrupted: {}", e.getMessage());
        }
    }
}
