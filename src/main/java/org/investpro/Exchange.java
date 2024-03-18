package org.investpro;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.ChoiceBox;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;

public class Exchange extends Coinbase {

    private static final Logger logger = LoggerFactory.getLogger(Exchange.class);
    static ChoiceBox<String> symbolsChoiceBox = new ChoiceBox<>();

    static {


        try {
            symbolsChoiceBox.getItems().addAll(getTradePairs());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Exchange(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);


    }

    static Collection<? extends String> getTradePairs() throws SQLException {

        //COINBASE URL TO GET ALL TRADES PAIRS
        String url = "https://api.pro.coinbase.com/products";
        HttpClient.Builder builder = HttpClient.newBuilder();
        HttpClient client = builder.build();
        HttpRequest.Builder request = HttpRequest.newBuilder();
        request.uri(URI.create(url));
        HttpResponse<String> response;
        ArrayList<String> tradePairs = new ArrayList<>();
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode res;
            try {
                res = OBJECT_MAPPER.readTree(response.body());
                logger.info(STR."coinbase response: \{res}");

                //coinbase response: [{"id":"DOGE-BTC","base_currency":"DOGE","quote_currency":"BTC","quote_increment":"0.00000001","base_increment":"0.1","display_name":"DOGE-BTC","min_market_funds":"0.000016","margin_enabled":false,"post_only":false,"limit_only":false,"cancel_only":false,"status":"online","status_message":"","trading_disabled":false,"fx_stablecoin":false,"max_slippage_percentage":"0.03000000","auction_mode":false,
                JsonNode rates = res;
                for (JsonNode rate : rates) {
                    CryptoCurrency baseCurrency, counterCurrency;


                    String fullDisplayName = rate.get("base_currency").asText();


                    String shortDisplayName = rate.get("base_currency").asText();
                    String code = rate.get("base_currency").asText();
                    int fractionalDigits = rate.get("base_increment").asInt();
                    String symbol = rate.get("base_currency").asText();
                    baseCurrency = new CryptoCurrency(fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, code);
                    String fullDisplayName2 = rate.get("quote_currency").asText();
                    String shortDisplayName2 = rate.get("quote_currency").asText();
                    String code2 = rate.get("quote_currency").asText();
                    int fractionalDigits2 = rate.get("quote_increment").asInt();
                    String symbol2 = rate.get("quote_currency").asText();

                    counterCurrency = new CryptoCurrency(
                            fullDisplayName2, shortDisplayName2, code2, fractionalDigits2, symbol2,
                            code2

                    );


                    TradePair tp = new TradePair(
                            baseCurrency, counterCurrency
                    );
                    @NotNull Character separator = Character.toUpperCase('/');
                    tradePairs.add(tp.toString(separator));

                    logger.info(Currency.getAvailableCurrencies().toString());
                }
            } catch (JsonProcessingException | ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return tradePairs;

    }

    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        String sym = symbolsChoiceBox.getValue();
        logger.info(sym);
        if (sym == null || sym.isEmpty()) {
            sym = "ETH/USD";
        }
        return TradePair.of(sym.split("/")[0],
                sym.split("/")[1]);
    }
}