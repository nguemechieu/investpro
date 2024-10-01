package org.investpro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;

import static org.investpro.Coinbase.client;
import static org.investpro.Coinbase.requestBuilder;

public class CryptoCurrencyDataProvider extends CurrencyDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(CryptoCurrencyDataProvider.class);
    private static final ArrayList<CoinInfo> coinInfoList = new ArrayList<>();
    private final ArrayList<Currency> coinsToRegister = new ArrayList<>();

    public CryptoCurrencyDataProvider() {
        logger.info("CryptoCurrencyDataProvider initialized.");
    }

    @Contract("_ -> new")
    private static HttpResponse<String> fetchJsonResponse(String jsonUrl) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(jsonUrl)).header("Accept", "application/json").GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(String.format("Failed to fetch data: HTTP error code %d", response.statusCode()));
        }

        return response;
    }

    public static ArrayList<CoinInfo> getCoinInfoList() {
        return coinInfoList;
    }

    public String getCurrencyImage(String baseCurrency) throws Exception {
        baseCurrency = "BTC";
        String jsonUrl = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=%s".formatted(baseCurrency);

        // Fetch and parse JSON response
        requestBuilder.uri(URI.create(jsonUrl));
        HttpResponse<String> jsonResponse = null;

        // Add a retry mechanism with delay
        int retries = 3;
        int delay = 2000; // delay in milliseconds
        while (retries > 0) {
            try {
                jsonResponse = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (jsonResponse.statusCode() != 200) {
                    throw new IOException(String.format("Failed to fetch data: HTTP error code %d", jsonResponse.statusCode()));
                }

                // Process the response here
                break; // Exit loop on successful response
            } catch (IOException e) {
                if (jsonResponse != null && jsonResponse.statusCode() == 429) {
                    logger.warn("Rate limit hit, retrying in %d seconds...".formatted(delay / 1000));
                    Thread.sleep(delay); // Sleep before retry
                } else {
                    throw e;
                }
            }
            retries--;
        }

        if (retries == 0) {
            throw new IOException("Failed to fetch data after retries.");
        }

        // Continue processing the response...
        return "Processed Image URL"; // Placeholder
    }

    public void registerCurrencies() throws Exception {
        String jsonUrl = "https://api.coingecko.com/api/v3/coins/list";

        HttpResponse<String> response = null;
        try {
            response = fetchJsonResponse(jsonUrl);
            // Process the response here
        } catch (IOException e) {
            if (e.getMessage().contains("429")) {
                logger.error("Rate limit exceeded: {}", e.getMessage());
            } else {
                logger.error("Failed to fetch data: {}", e.getMessage());
            }
        }

        assert response != null;

        Gson gson = new Gson();
        JsonArray coinArray = gson.fromJson(response.body(), JsonArray.class);

        for (JsonElement element : coinArray) {
            JsonObject coinJson = element.getAsJsonObject();
            String id = coinJson.get("id").getAsString();
            String symbol = coinJson.get("symbol").getAsString().toUpperCase();

            String image = getCurrencyImage(id);

            logger.info("Currency: {}, Image: {}", symbol, image);
        }
    }
}
