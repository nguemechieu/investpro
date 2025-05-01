package org.investpro.investpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class CryptoCurrencyDataProvider extends CurrencyDataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CryptoCurrencyDataProvider.class);
    private static final String COINGECKO_API_URL = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd";
    private static final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

    public CryptoCurrencyDataProvider() {
    }

    public static void registerCurrencies() {
        try {
            List<CryptoCurrency> coinsToRegister = new ArrayList<>();
            String jsonResponse = fetchCryptoData();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode cryptoCoinsJson = objectMapper.readValue(jsonResponse, JsonNode.class);
            logger.info("Registered {} cryptocurrencies.", cryptoCoinsJson);

            for (JsonNode coinJson : cryptoCoinsJson) {
                String homeUrl = coinJson.has("homepage") && !coinJson.get("homepage").isEmpty()
                        ? coinJson.get("homepage").get(0).asText()
                        : "https://coingecko.com";

                String walletUrl = "https://coingecko.com";  // Placeholder, API doesn't provide wallet URLs

                coinsToRegister.add(new CryptoCurrency(
                        coinJson.get("name").asText(),
                        coinJson.get("symbol").asText().toUpperCase(),
                        coinJson.get("symbol").asText().toUpperCase(),
                        8,
                        coinJson.get("symbol").asText().toUpperCase(),
                        CryptoCurrencyAlgorithms.getAlgorithm("SHA256"),  // Placeholder, as CoinGecko doesn't provide this
                        homeUrl,
                        walletUrl,
                        coinJson.get("atl_date").asText().hashCode(), // Placeholder for genesis time
                        2016,
                        coinJson.get("max_supply").asText()
                ));
            }

            // Adding Bitcoin manually (in case API data is missing)
            coinsToRegister.add(new CryptoCurrency(
                    "Bitcoin",
                    "BTC",
                    "BTC",
                    8,
                    "Ƀ",
                    CryptoCurrencyAlgorithms.getAlgorithm("SHA256"),
                    "https://bitcoin.org",
                    "https://bitcoin.org/en/download",
                    1231006505,
                    2016,
                    "21000000"
            ));

            CurrencyDataProvider.registerCurrencies();
        } catch (Exception e) {
            logger.error("Failed to register cryptocurrencies: {}", e.getMessage());
        }
    }

    private static String fetchCryptoData() {
        try {
            URI url = URI.create(COINGECKO_API_URL);
            requestBuilder.uri(url);
            HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            Scanner scanner = new Scanner(connection.getInputStream());
            StringBuilder responseBody = new StringBuilder();
            while (scanner.hasNextLine()) {
                responseBody.append(scanner.nextLine());
            }
            scanner.close();
            String res = responseBody.toString();
            logger.info("Response{}", res);
            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
