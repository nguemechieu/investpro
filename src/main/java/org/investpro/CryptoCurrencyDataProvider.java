package org.investpro;



import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.out;


public class CryptoCurrencyDataProvider  {


    public CryptoCurrencyDataProvider() throws SQLException, ClassNotFoundException {

    }


    public  void registerCurrencies() {

        HttpRequest.Builder request = HttpRequest.newBuilder();
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        request.uri(URI.create("https://api.exchange.coinbase.com/currencies"));

//
//        "id": "GST",
//                "name": "Green Satoshi Token",
//                "min_size": "0.00000001",
//                "status": "online",
//                "message": "",
//                "max_precision": "0.00000001",
//                "convertible_to": [],
//        "details": {
//            "type": "crypto",
//                    "symbol": null,
//                    "network_confirmations": 31,
//                    "sort_order": 0,
//                    "crypto_address_link": "https://explorer.solana.com/address/{{address}}",
//                    "crypto_transaction_link": "https://explorer.solana.com/tx/{{txId}}",
//                    "push_payment_methods": [],
//            "group_types": [],
//            "display_name": null,
//                    "processing_time_seconds": null,
//                    "min_withdrawal_amount": 0.01,
//                    "max_withdrawal_amount": 2120000

        HttpClient.newHttpClient().sendAsync(
                        request.build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {


                    JSONObject json = new JSONObject(response);


                    //   "name": "Green Satoshi Token",
//                "min_size": "0.00000001",
//                "status": "online",
//                "message": "",
//                "max_precision": "0.00000001",


                    for (int i = 0; i < json.length(); i++) {
                        String id = "";
                        String name = "";
                        String status = "";
                        String symbol = "";
                        String message = "";
                        String max_precision = "";
                        int maxPrecision = 0;
                        String type = "";
                        String crypto_address_link = null;
                        if (json.has("id")) {
                            id = json.getString("id");
                        }

                        if (json.has("name")) {

                            name = json.getString("name");
                        }
                        if (json.has("status")) {

                            status = json.getString("status");
                        }
                        if (json.has("message")) {
                            message = json.getString("message");

                        }
                        if (json.has("symbol")) {
                            symbol = json.getString("symbol");
                        }
                        if (
                                json.has("max_precision")
                        ) {
                            maxPrecision = json.getInt("max_precision");
                        }

                        if (json.has("details")) {


                            type = json.getJSONObject("details").getString("type");
                            crypto_address_link = json.getJSONObject("details").getString("crypto_address_link");

                        }


                        out.println("currency id" + id);
                        List<Currency> coinsToRegister = new ArrayList<>();


                        String image=
                                "https://api.coinmarketcap.com/v1/ticker/" + id + "/?convert=USD";
                        out.println("image " + image);
                    //    coinsToRegister.add(i, new CryptoCurrency(name, id, symbol, maxPrecision, crypto_address_link,name,image));

                        //        String fullDisplayName, String shortDisplayName, String code, int fractionalDigits,
                        //      String symbol

                        Currency.registerCurrencies(coinsToRegister);
                        out.println("currency id " + id);
                        out.println("Coin to register " + coinsToRegister);

                    }
                    out.println("Cannot register currencies");
                    return null;
                });

//
//        List<Currency> coinsToRegister = null;

//        // crypto_coins.json is encoded in UTF-8 (for symbols)
//        JSONObject cryptoCoinsJson =new JSONObject( Files.readString(Paths.get(Objects.requireNonNull(CryptoCurrencyDataProvider.class.getResource("/crypto_coins.json")).toURI())));
//        coinsToRegister = new ArrayList<>();
//
//
//        if (cryptoCoinsJson.has("currencies")) {
//            JSONArray cryptoCoins = new JSONArray(cryptoCoinsJson);
//            for (Object coinJson : cryptoCoins) {
//            URL co = Objects.requireNonNull(coinJson.getClass().getClassLoader().getResource("coin")).toURI().toURL();
//
//
//                Expression<Object> coinInfo
//                String homeUrl = coinInfo.get("homeUrl").toString();
//            if (homeUrl.equals("?")) {
//                homeUrl = "google.com";
//            }
//            String walletUrl = coinInfo.get("walletUrl").toString();
//            if (walletUrl.equals("?")) {
//                walletUrl = "google.com";
//            }
//            Integer genesisTime = coinInfo.get("genesisTime").getInt("genesisTime");
//            if (genesisTime.equals(-1)) {
//                genesisTime = 0;
//            }
//            coinsToRegister.add(new CryptoCurrency(
//                    coinInfo.get("fullDisplayName").getString("fullDisplayName"),
//                    coinInfo.get("shortDisplayName").getString("shortDisplayName"),
//                    coinInfo.get("code").getString("code"),
//                    coinInfo.get("fractionalDigits").getInt("fractionalDigits"),
//                    coinInfo.get("symbol").getString("symbol"),
//                    CryptoCurrencyAlgorithms.getAlgorithm(coinInfo.get("algorithm").getString("algorithm")),
//                    homeUrl,
//                    walletUrl,
//                    genesisTime,
//                    coinInfo.get("difficultyRetarget").getInt("difficultyRetarget"),
//                    coinInfo.get("maxCoinsIssued").getString("maxCoinsIssued")
//            ));
//
//        }}else
//        {
//            Log.e(TAG, "No coin info available");
//        }
    }
}

