package org.investpro.investpro;


import java.util.ArrayList;
import java.util.List;


public class CryptoCurrencyDataProvider extends CurrencyDataProvider {


    public CryptoCurrencyDataProvider() {
    }

    @Override
    protected void registerCurrencies() {
        List<Currency> coinsToRegister = new ArrayList<>();
        coinsToRegister.add(new CryptoCurrency(
                "",
                "",
                "BTC",
                8,
                "",
                CryptoCurrencyAlgorithms.getAlgorithm("SHA256"),
                "https://api.exchange.coinbase.com/currencies/",
                "https://bitcoin.org/en/download",
                1231006505,
                2016,
                "21000000"
        ));


//
//        List<Currency> coinsToRegister = null;
        Currency.registerCurrencies(coinsToRegister);
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

