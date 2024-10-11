package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.investpro.Coinbase.client;
import static org.investpro.Coinbase.requestBuilder;
import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;
import static org.investpro.Exchange.logger;

public class ExchangeInfo {

    private static final String BINANCE_API_URL = "https://api.binance.us";
    private static final String EXCHANGE_INFO_ENDPOINT = "/api/v3/exchangeInfo";

    public List<TradePair>symbols= new ArrayList<>();

    public ExchangeInfo() {
          }

    // Method to fetch and parse exchange info from Binance US
    public static @NotNull ExchangeData fetchExchangeInfo() throws Exception {
        // Construct HTTP request
       requestBuilder
                .uri(URI.create(BINANCE_API_URL + EXCHANGE_INFO_ENDPOINT))

           ;

        // Send the HTTP request and retrieve the response
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch exchange info: " + response.body());
        }

        // Parse the response body as JSON
        JsonNode root;
        try {
            root =OBJECT_MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing exchange info: " + e.getMessage(), e);
        }

        // Extract data from the response
        ExchangeData exchangeData = new ExchangeData();
        exchangeData.setTimezone(root.get("timezone").asText());
        exchangeData.setServerTime(root.get("serverTime").asLong());

        // Extract rate limits
        JsonNode rateLimits = root.get("rateLimits");
        if (rateLimits.isArray()) {
            for (JsonNode rateLimit : rateLimits) {
                exchangeData.addRateLimit(new RateLimit(
                        rateLimit.get("rateLimitType").asText(),
                        rateLimit.get("interval").asText(),
                        rateLimit.get("intervalNum").asInt(),
                        rateLimit.get("limit").asInt()
                ));
            }
        }

        // Extract symbols (trading pairs)
        JsonNode symbols = root.get("symbols");
        if (symbols.isArray()) {
            for (JsonNode symbol : symbols) {
                exchangeData.addSymbol(new SymbolInfo(
                        symbol.get("symbol").asText(),
                        symbol.get("baseAsset").asText(),
                        symbol.get("quoteAsset").asText(),
                        symbol.get("status").asText()
                ));

                logger.info("Added symbol{}", new TradePair(symbol.get("baseAsset").asText(), symbol.get("quoteAsset").asText()));
            }
        }

        return exchangeData;
    }

    // Main method to test fetching exchange info
    public static void main(String[] args) {
        try {
            ExchangeInfo exchangeInfo = new ExchangeInfo();
            logger.info("Fetching exchange info...{}", exchangeInfo);
            ExchangeData data = fetchExchangeInfo();
            logger.info("Fetching exchange info{}", data);
        } catch (Exception e) {
            logger.info("Failed to fetch exchange info", e);
        }
    }

    @Override
    public String toString() {
        return "ExchangeInfo{" +
                "symbols=" + symbols +
                '}';
    }

    // ExchangeData class to hold the extracted exchange information
    public static class ExchangeData {
        private String timezone;
        private long serverTime;
        private final List<RateLimit> rateLimits = new ArrayList<>();
        private final List<SymbolInfo> symbols = new ArrayList<>();

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public long getServerTime() {
            return serverTime;
        }

        public void setServerTime(long serverTime) {
            this.serverTime = serverTime;
        }

        public List<RateLimit> getRateLimits() {
            return rateLimits;
        }

        public void addRateLimit(RateLimit rateLimit) {
            this.rateLimits.add(rateLimit);
        }

        public List<SymbolInfo> getSymbols() {
            return symbols;
        }

        public void addSymbol(SymbolInfo symbolInfo) {
            this.symbols.add(symbolInfo);
        }

        @Override
        public String toString() {
            return "ExchangeData{" +
                    "timezone='" + timezone + '\'' +
                    ", serverTime=" + serverTime +
                    ", rateLimits=" + rateLimits +
                    ", symbols=" + symbols +
                    '}';
        }
    }

    // Class to hold rate limit information
    public record RateLimit(String rateLimitType, String interval, int intervalNum, int limit) {

        @Contract(pure = true)
        @Override
        public @NotNull String toString() {
            return "RateLimit{" +
                    "rateLimitType='" + rateLimitType + '\'' +
                    ", interval='" + interval + '\'' +
                    ", intervalNum=" + intervalNum +
                    ", limit=" + limit +
                    '}';
        }
    }
}
