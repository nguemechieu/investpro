package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael Ennen
 */
public class CryptoCurrencyDataProvider extends CurrencyDataProvider {

    public CryptoCurrencyDataProvider() {
    }

    @Override
    protected void registerCurrencies() {


        List<Currency> coinsToRegister = new ArrayList<>();


        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder request = HttpRequest.newBuilder();
        request.header("Accept", "application/json");
        request.header("Content-Type", "application/json");
        request.uri(URI.create("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false"));

        HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.statusCode());
        }
        String json = response.body();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonObject;
        try {
            jsonObject = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        for (JsonNode jsonObject1 : jsonObject) {
            coinsToRegister.add(new CryptoCurrency(
                    jsonObject1.get("name").asText(), jsonObject1.get("name").asText(), jsonObject1.get("id").asText(),
                    jsonObject1.get("current_price").asInt(),
                    jsonObject1.get("symbol").asText(), jsonObject1.get("image").asText()

            ));

            Log.info("currencies ", coinsToRegister.toString());
        }


//
//        "id": "bitcoin",
//                "symbol": "btc",
//                "name": "Bitcoin",
//                "image": "https://assets.coingecko.com/coins/images/1/large/bitcoin.png?1547033579",
//                "current_price": 25685,
//                "market_cap": 498031043912,
//                "market_cap_rank": 1,
//                "fully_diluted_valuation": 539273611145,
//                "total_volume": 18392320085,
//                "high_24h": 27075,
//                "low_24h": 25444,
//                "price_change_24h": -1330.2986579932913,
//                "price_change_percentage_24h": -4.92432,
//                "market_cap_change_24h": -26308763638.299133,
//                "market_cap_change_percentage_24h": -5.0175,
//                "circulating_supply": 19393962,
//                "total_supply": 21000000,
//                "max_supply": 21000000,
//                "ath": 69045,
//                "ath_change_percentage": -62.7813,
//                "ath_date": "2021-11-10T14:24:11.849Z",
//                "atl": 67.81,
//                "atl_change_percentage": 37796.98149,
//                "atl_date": "2013-07-06T00:00:00.000Z",
//                "roi": null,
//                "last_updated": "2023-06-06T01:16:11.958Z"


        Currency.registerCurrencies(coinsToRegister);
    }
}
