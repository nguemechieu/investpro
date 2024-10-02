package org.investpro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.investpro.Coinbase.client;

public class CryptoCurrencyDataProvider extends CurrencyDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(CryptoCurrencyDataProvider.class);
    private static final ArrayList<CoinInfo> coinInfoList = new ArrayList<>();
    private final ArrayList<Currency> coinsToRegister = new ArrayList<>();
    private static final int REQUEST_DELAY = 1000 * 60 * 6; // 2000 ms delay between requests

    public CryptoCurrencyDataProvider() {
        logger.info("CryptoCurrencyDataProvider initialized.");
    }

    private static @NotNull HttpResponse<String> fetchJsonResponse(String jsonUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jsonUrl))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {

            logger.error("Error fetching response:%s".formatted(response.body())
            );
        }

        return response;
    }

    public static ArrayList<CoinInfo> getCoinInfoList() {
        return coinInfoList;
    }

    public String getCurrencyImage(String baseCurrency) throws Exception {
        String jsonUrl = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=%s".formatted(baseCurrency);

        HttpResponse<String> jsonResponse = null;
        int retries = 3;
        int delay = 1000 * 60 * 6; // 6 minutes



        while (retries > 0) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jsonUrl))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                jsonResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (jsonResponse.statusCode() == 200) {
                    logger.info("Image fetched successfully for base currency: {}", baseCurrency);
                    break;
                } else {
                    logger.error("Failed to fetch data: HTTP error code {}", jsonResponse.statusCode());
                }
            } catch (IOException e) {
                logger.error("Error fetching currency image: {}", e.getMessage());
                if (jsonResponse != null && jsonResponse.statusCode() == 429) {
                    logger.warn("Rate limit hit, retrying in {} seconds...", delay / 1000);
                    Thread.sleep(delay);
                } else {
                    throw e; // Throw exception for other IOExceptions
                }
            }
            retries--;
        }

        if (retries == 0) {
            logger.error("Failed to fetch image after retries.");
            return "N/A"; // Fallback
        }

        JsonArray jsonArray = new Gson().fromJson(jsonResponse.body(), JsonArray.class);
        if (!jsonArray.isEmpty()) {
            JsonObject coinData = jsonArray.get(0).getAsJsonObject();
            return coinData.get("image").getAsString(); // Assuming the API returns an "image" field
        }

        return "N/A"; // Fallback if no image is found
    }

    public void registerCurrencies() throws Exception {
        String jsonUrl = "https://api.coingecko.com/api/v3/coins/list";

        HttpResponse<String> response = fetchJsonResponse(jsonUrl);
        Gson gson = new Gson();
        JsonArray coinArray = gson.fromJson(response.body(), JsonArray.class);

        for (JsonElement coinElement : coinArray) {
            JsonObject coinJson = coinElement.getAsJsonObject();
            String id = coinJson.get("id").getAsString();
            String symbol = coinJson.get("symbol").getAsString().toUpperCase();
            String name = coinJson.get("name").getAsString();

            CryptoCurrency coin = new CryptoCurrency(name, id, id, 8, symbol, name);
            coinsToRegister.add(coin);

            logger.info("Registered currency: {}", coin);
        }

        // Schedule requests with a delay of 2000 ms between them
        ScheduledExecutorService scheduler = newScheduledThreadPool(1);
        Runnable task = new Runnable() {
            int index = 0;

            @Override
            public void run() {
                if (index < coinsToRegister.size()) {
                    Currency coin = coinsToRegister.get(index);
                    try {
                        coin.image = getCurrencyImage(coin.code);
                        logger.info("Updated currency {} with image URL: {}", coin.code, coin.image);
                    } catch (Exception e) {
                        logger.error("Failed to update image for currency {}: {}", coin.code, e.getMessage());
                        coin.image = "N/A"; // Set fallback if image fetching fails
                    }
                    index++;
                } else {
                    // Shut down the scheduler once all coins are processed
                    scheduler.shutdown();
                }
            }
        };

        // Schedule the task with a fixed delay between executions
        scheduler.scheduleWithFixedDelay(task, 0, REQUEST_DELAY, TimeUnit.MILLISECONDS);
    }
}
