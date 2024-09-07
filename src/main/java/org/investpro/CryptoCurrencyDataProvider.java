package org.investpro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.investpro.Coinbase.client;
import static org.investpro.Coinbase.requestBuilder;
import static org.investpro.Currency.*;

public class CryptoCurrencyDataProvider extends CurrencyDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(CryptoCurrencyDataProvider.class);

    ArrayList<Currency> coinsToRegister = new ArrayList<>();

    public CryptoCurrencyDataProvider() {
    }

    @Contract("_ -> new")
    private static @NotNull String fetchJsonResponse(String jsonUrl) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(jsonUrl))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(String.format("Failed to fetch da" +
                    "ta: HTTP error code %d", response.statusCode()));
        }

        return response.body();
    }

    public void get(String baseCurrency) throws Exception {
        String jsonUrl = String.format(
                """
                        https://api.coingecko.com/api/v3/coins/markets?vs_currency=%s""", baseCurrency
        );

        // Fetch and parse JSON response
        String jsonResponse = fetchJsonResponse(jsonUrl);

        Gson gson = new Gson();
        JsonArray coinArray = gson.fromJson(jsonResponse, JsonArray.class);


        logger.info("Registering cryptocurrencies...");
        logger.info("-------------------------");
        for (JsonElement element : coinArray) {
            JsonObject coinJson = element.getAsJsonObject();

            String fullDisplayName = coinJson.get("name").getAsString();
            String shortDisplayName = coinJson.get("symbol").getAsString().toUpperCase();
            String code = coinJson.get("symbol").getAsString().toUpperCase();
            int fractionalDigits = coinJson.get("decimal").getAsInt();
            String symbol = coinJson.get("symbol").getAsString();

            coinsToRegister.add(new CryptoCurrency(
                    fullDisplayName,
                    shortDisplayName,
                    code,
                    fractionalDigits,
                    symbol, symbol
            ));
        }

        for (JsonElement element : coinArray) {
            JsonObject coinJson = element.getAsJsonObject();

            String fullDisplayName = coinJson.get("name").getAsString();
            String shortDisplayName = coinJson.get("symbol").getAsString().toUpperCase();
            String code = coinJson.get("symbol").getAsString().toUpperCase();
            int fractionalDigits = coinJson.get("decimal").getAsInt();
            String symbol = coinJson.get("symbol").getAsString();
            String algorithm = coinJson.has("algorithm") ? coinJson.get("algorithm").getAsString() : "Unknown";
            String homeUrl = coinJson.has("home_url") ? coinJson.get("home_url").getAsString() : "N/A";
            String walletUrl = coinJson.has("wallet_url") ? coinJson.get("wallet_url").getAsString() : "N/A";
            int genesisTime = coinJson.has("genesis_time") ? coinJson.get("genesis_time").getAsInt() : 0;
            int difficultyRetarget = coinJson.has("difficulty_retarget") ? coinJson.get("difficulty_retarget").getAsInt() : 0;
            String maxCoinsIssued = coinJson.has("max_coins_issued") ? coinJson.get("max_coins_issued").getAsString() : "Unknown";
            logger.info("Coin: ({}), Algorithm: {}, Home URL: {}, Wallet URL", code, fullDisplayName, algorithm, homeUrl, walletUrl, genesisTime, difficultyRetarget, maxCoinsIssued);

            logger.info("{}", coinJson);
            coinsToRegister.add(new CryptoCurrency(
                    fullDisplayName,
                    shortDisplayName,
                    code,
                    fractionalDigits,
                    symbol, symbol
            ));
        }
        db1.save(coinsToRegister);
    }

    public void registerCurrencies() throws Exception {
        String jsonUrl = "https://api.coingecko.com/api/v3/coins/list";

        // Fetch and parse JSON response
        String jsonResponse = fetchJsonResponse(jsonUrl);

        Gson gson = new Gson();
        JsonArray coinArray = gson.fromJson(jsonResponse, JsonArray.class);
        ArrayList<Currency> cryptoLists = new ArrayList<>(

        );
        cryptoLists.add(NULL_CRYPTO_CURRENCY);

        for (JsonElement element : coinArray) {
            JsonObject coinJson = element.getAsJsonObject();
            String id = coinJson.get("id").getAsString();
            String symbol = coinJson.get("symbol").getAsString().toUpperCase();

            CryptoCurrency currency = new CryptoCurrency(coinJson.get("name").getAsString(), symbol, symbol, 8
                    , symbol, id);
            cryptoLists.add(currency);

        }
        db1.save(cryptoLists);
        //   CURRENCIES.put(new SymmetricPair(CurrencyType.CRYPTO,cryptoLists.stream()), (Currency) cryptoLists
        //    .stream().collect(Collectors.toMap(Currency::getCode, Function.identity(), (existing, replacement) -> existing)));

    }

    // protected Currency register(Currency currency) throws Exception {}
}
