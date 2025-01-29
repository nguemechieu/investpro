package org.investpro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;

import static org.investpro.Coinbase.client;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CurrencyDataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyDataProvider.class);
    @Getter
    private static final ArrayList<CoinInfo> coinInfoList = new ArrayList<>();
    private final ArrayList<Currency> coinsToRegister = new ArrayList<>();

    public CurrencyDataProvider() {
        logger.info("CryptoCurrencyDataProvider initialized.");
    }

    public static void save(ArrayList<Currency> collect) {

    }


    public void registerCurrencies() throws Exception {
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

                logger.error("Failed to parse JSON array." + jsonResponse);


                return; // Assuming the API returns an "image" field
            }


            for (JsonElement coinElement : jsonArray) {
                JsonObject coinJson = coinElement.getAsJsonObject();
                String id = coinJson.get("id").getAsString();
                String symbol = coinJson.get("symbol").getAsString().toUpperCase();
                String name = coinJson.get("name").getAsString();
                Double currentPrice = coinJson.get("current_price").getAsDouble();
                String image = coinJson.get("image").getAsString();
                long marketCap = coinJson.get("market_cap").getAsLong();
                int marketCapRank = coinJson.get("market_cap_rank").getAsInt();
                int fractional_digit = coinJson.get("current_price").getAsString().length() - 2;
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


                Currency coin = new Currency(CurrencyType.CRYPTO, name, id, symbol, fractional_digit, symbol, image);


                coinsToRegister.add(coin);


            }
            for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {
                Currency coin = new Currency(CurrencyType.FIAT, currency.getDisplayName(), currency.getCurrencyCode(), currency.getCurrencyCode(), 3, currency.getSymbol(), currency.getDisplayName());
                coinsToRegister.add(coin);


            }

            save(coinsToRegister);


        } catch (IOException | InterruptedException e) {
            logger.error("Error fetching data: {}", e.getMessage());
        }
    }
}
