package org.investpro;

import com.fasterxml.uuid.Generators;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.UUID;

import static org.investpro.InvestPro.db1;
import static org.investpro.exchanges.Coinbase.client;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CurrencyDataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyDataProvider.class);
    @Getter
    private static final ArrayList<CoinInfo> coinInfoList = new ArrayList<>();
    private static final ArrayList<Currency> coinsToRegister = new ArrayList<>();

    public CurrencyDataProvider() {
        logger.info("CryptoCurrencyDataProvider initialized.");
    }


    public static void save(@NotNull ArrayList<Currency> collect) {
        for (Currency currency : collect) {
            if (currency == null) continue;

            if (db1.getCurrency(currency.getCode()) == null) {
                db1.save(currency);
                Currency.CURRENCIES.put(currency.currencyType, currency);
            }
        }
    }


    public static void registerCurrencies() {
        String jsonUrl = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=BTC";

        HttpResponse<String> jsonResponse;


        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(jsonUrl))
                    .header("Accept", "application/json")
                    .header(
                            "Content-Type", "application/json"
                    );

            jsonResponse = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

            if (jsonResponse.statusCode() != 200) {


                logger.error("Failed to fetch data: HTTP error code {}", jsonResponse.statusCode()
                        + " response " + jsonResponse.body()
                );

                throw new RuntimeException("Failed to fetch coins" + jsonResponse.statusCode());
            }


            JsonArray jsonArray = new Gson().fromJson(jsonResponse.body(), JsonArray.class);


            if (jsonArray.isEmpty()) {

                logger.error("Failed to parse JSON array.{}", jsonResponse);


                return; // Assuming the API returns an "image" field
            }


            for (JsonElement coinElement : jsonArray) {
                JsonObject coinJson = coinElement.getAsJsonObject();
                String id = coinJson.has("id") ? coinJson.get("id").getAsString() : "UNKNOWN";
                String symbol = coinJson.has("symbol") ? coinJson.get("symbol").getAsString().toUpperCase() : "UNK";
                String name = coinJson.has("name") ? coinJson.get("name").getAsString() : "Unnamed";
                double currentPrice = coinJson.has("current_price") ? coinJson.get("current_price").getAsDouble() : 0.0;
                String image = coinJson.has("image") ? coinJson.get("image").getAsString() : "default.png";
                long marketCap = coinJson.has("market_cap") ? coinJson.get("market_cap").getAsLong() : 0L;
                int marketCapRank = coinJson.has("market_cap_rank") ? coinJson.get("market_cap_rank").getAsInt() : -1;


                   int fractional_digit = (currentPrice == 0) ? 2 : String.valueOf(currentPrice).split("\\.")[1].length();

                CoinInfo coinInfo = new CoinInfo();

                coinInfo.setId(id);
                coinInfo.setSymbol(symbol);
                coinInfo.setName(name);
                coinInfo.setCurrentPrice(currentPrice);
                coinInfo.setImage(image);
                coinInfo.setMarketCap(marketCap);
                coinInfo.setMarketCapRank(marketCapRank);
                coinInfo.setFractionalDigits(fractional_digit);
                coinInfoList.add(coinInfo);

                logger.info("Added coin: {}", coinInfo);

                UUID currencyId = Generators.timeBasedGenerator().generate(); // Generate UUID v1

                CurrencyType type = (name.equalsIgnoreCase("Bitcoin") || name.equalsIgnoreCase("Ethereum")) ? CurrencyType.CRYPTO : CurrencyType.FIAT;

                Currency coin = new Currency(type.name(), name, id, symbol, fractional_digit, symbol, image);




                coinsToRegister.add(coin);


            }
            for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {

                // Generate UUID v1

                Currency coin = new Currency(CurrencyType.FIAT.name(), currency.getDisplayName(), currency.getCurrencyCode(), currency.getCurrencyCode(), 3, currency.getSymbol(), currency.getDisplayName()) ;




                coinsToRegister.add(coin);


            }

            db1.save(coinsToRegister.stream().toList().getFirst());


        } catch (IOException | InterruptedException e) {
            logger.error("Error fetching data: {}", e.getMessage());
        }
    }
}
