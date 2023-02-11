package org.investpro.investpro.BinanceUs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.investpro.investpro.*;
import org.investpro.investpro.Coinbase.Coinbase;
import org.investpro.investpro.oanda.CandleDataSupplier;
import org.investpro.investpro.oanda.OandaCandleStickChartToolbar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.lang.System.out;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Binance {
    public static final String API_PUBLIC_KEY = "";
    public static final String API_SECRET_KEY = "";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static String apiKey;

    public static final String API_URL = "https://api.binance.com";
    public static final String API_VERSION = "v1";
    public static final String API_USER = "";
    public static final String API_PASS = "";
    public static final String TESTNET_API_URL = "https://testnet.binance.com";
    public static final String TESTNET_API_VERSION = "v1";
    public static final String TESTNET_API_USER = "";
    public static final String TESTNET_API_PASS = "";
    public static final String MAINNET_API_URL = "https://api.mainnet.binance.com";
    public static final String MAINNET_API_VERSION = "v1";
    public static final String MAINNET_API_USER = "";
    public static final String MAINNET_API_PASS = "";
    public static final String TESTNET_TESTNET_API_URL = "https://testnet." + "binance.org";
    public static final String TESTNET_TESTNET_API_VERSION = "v1";
    String BTC_USD = "ETHUSD";
    private String apiSecret;
    private String apiPass;


    public Binance(
            String apiKey,
            String apiSecret,
            String apiPass
    ) throws Exception {

        if (apiKey == null || apiSecret == null || apiPass == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Please fill in all the required fields",
                    ButtonType.OK);
            alert.setTitle("Binance");
            alert.setHeaderText("Please fill in all the required fields");
            alert.showAndWait();

            throw new Exception("apiKey, apiSecret and apiPass are required");
        } else {
            Binance.apiKey = apiKey;
            this.apiSecret = apiSecret;
            this.apiPass = apiPass;
        }

    }

    public static Collection<? extends Order> getOrders() {
        return new ArrayList<>();
    }

    public void init() {

    }

    public Binance() {
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getApiPass() {
        return apiPass;
    }

    public static String getApiKey() {
        return apiKey;
    }

    public static String getSecretKey() {
        return API_SECRET_KEY;
    }

    public static String getBaseUrl() {
        return API_URL;
    }

    public static String getTestnetBaseUrl() {
        return TESTNET_API_URL;
    }

    public static String getVersion() {
        return API_VERSION;
    }

    public static boolean isTestnet() {
        return true;
    }

    public static boolean isPublicNetwork() {
        return true;
    }

    public static boolean isRateLimit() {
        return true;
    }

    public static boolean isMarginMode() {
        return true;
    }

    public static boolean isDemoMode() {
        return true;
    }

    public static boolean isAutoOpenBrowser() {
        return true;
    }

    public static boolean isTorEnabled() {
        return true;
    }

    public static boolean isTorSSLEnabled() {
        return true;
    }

    public static boolean isTorProxyEnabled() {
        return true;
    }

    public static boolean isTorNoProxyEnabled() {
        return true;
    }

    public static boolean isTorDnsEnabled() {
        return true;
    }

    public static boolean isTorFallbackEnabled() {
        return true;
    }

    public static boolean isDebug() {
        return true;
    }

    public static void createMarketOrder(String tradePair, String type, String side, double size) {
    }

    public BinanceUsCandleStickChartContainer start() throws URISyntaxException, IOException {

        BinanceUsCandleStickChartContainer candleStickChartContainer;

        candleStickChartContainer = new BinanceUsCandleStickChartContainer(new BinanceU(), BTC_USD, true);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }

    //makeRequest return JSONObject
    @Contract("_, _ -> new")
    private @NotNull JSONObject makeRequest(String url, String method) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept", "html/text");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Accept-Charset", "utf-8");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
        conn.setRequestProperty("CB-ACCESS-KEY", API_PUBLIC_KEY);//	API key as a string
        String timestamp = new Date().toString();
        String body = null;
        conn.setRequestProperty("CB-ACCESS-SIGN", timestamp + method + url + body);
        //"base64-encoded signature (see Signing a Message)");
        conn.setRequestProperty("CB-ACCESS-TIMESTAMP", new Date().toString());//	Timestamp for your request
        conn.setRequestProperty("CB-ACCESS-PASSPHRASE", API_SECRET_KEY);//Passphrase you specified when creating the API key
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0" + ";q=0.9,en-GB;q=0.8,en-US;q=0.7,en;q=0.6");
        conn.setRequestProperty("Host", "https://api.binance.us");
        conn.setRequestProperty("Origin", "https://api.binance.us");
        conn.setRequestProperty("Sec-Fetch-Mode", "cors");
        conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.connect();
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        System.out.println("COINBASE " + response);
        in.close();

        return new JSONObject(response.toString());
    }
    public  class BinanceU extends Exchange {   // private static final URI urO=URI.create("wss://stream.binance.us:9443");


        private static String x;
        private static String str;

        BinanceU() {
            super(null); // This argument is for creating a WebSocket client for live trading data.
        }


        @Override
        public Coinbase.CoinbaseCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, String tradePair) {
            return null;
        }

        /**
         * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
         * <p>
         * This method only needs to be implemented to support live syncing.
         */
        @Override
        public CompletableFuture<List<Trade>> fetchRecentTradesUntil(String tradePair, Instant stopAt) {
            Objects.requireNonNull(tradePair);
            Objects.requireNonNull(stopAt);

            if (stopAt.isAfter(Instant.now())) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

            // It is not easy to fetch trades concurrently because we need to get the "cb-after" header after each request.
            CompletableFuture.runAsync(() -> {
                IntegerProperty afterCursor = new SimpleIntegerProperty(-1);
                List<Trade> tradesBeforeStopTime = new ArrayList<>();

                // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
                // burst.
                // We will know if we get rate limited if we get a 429 response code.
                // FIXME: We need to address this!
                for (int i = 0; !futureResult.isDone(); i++) {
                    String uriStr = "https://api.binance.us/api/v3/trades?symbol=" + tradePair;

                    if (i != 0) {
                        uriStr += "?after=" + afterCursor.get();
                    }

                    try {
                        HttpRequest.Builder req = HttpRequest.newBuilder();
                        req.header("Accept", "application/json");
                        req.header("Authorization", Binance.getApiKey());
                        req.uri(new URI(uriStr));
                        HttpResponse<String> response = HttpClient.newHttpClient().send(
                                req.build(),
                                HttpResponse.BodyHandlers.ofString());

                        Log.info("response headers: " + response.headers());
                        if (response.headers().firstValue("cb-after").isEmpty()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "Binance Us trades response did not contain header \"cb-after\": " + response));
                            return;
                        }

                        afterCursor.setValue(Integer.valueOf((response.headers().firstValue(" cb-after").get())));

                        JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());
                        if (!tradesResponse.isArray()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "Trades response was not an array!"));
                        }
                        if (tradesResponse.isEmpty()) {
                            futureResult.completeExceptionally(new IllegalArgumentException("tradesResponse was empty"));
                        } else {
                            for (int j = 0; j < tradesResponse.size(); j++) {
                                JsonNode trade = tradesResponse.get(j);
                                Instant time = Instant.from(ISO_INSTANT.parse(trade.get("time").asText()));
                                if (time.compareTo(stopAt) <= 0) {
                                    futureResult.complete(tradesBeforeStopTime);
                                    break;
                                } else {
                                    tradesBeforeStopTime.add(new Trade(tradePair,
                                            DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.substring(4, tradePair.length() - 1)),
                                            DefaultMoney.ofCrypto(trade.get("qty").asText(), tradePair.substring(0, 3)),
                                            Side.getSide(trade.get("isBuyerMaker").asText()), trade.get("id").asLong(), time));
//                                    "id": 981492,
//                                            "price": "0.00380100",
//                                            "qty": "0.22000000",
//                                            "quoteQty": "0.00083622",
//                                            "time": 1637128016269,
//                                            "isBuyerMaker": false,
//                                            "isBestMatch": true
                                }
                            }
                        }
                    } catch (IOException | InterruptedException ex) {
                        Log.error("ex: " + ex);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            return futureResult;
        }

        /**
         * This method only needs to be implemented to support live syncing.
         */
        @Override
        public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
                String tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
//            String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
//                    currentCandleStartedAt, ZoneOffset.UTC));
//            long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
//            //  Get the closest supported granularity to the ideal granularity.
//            int actualGranularity = getCandleDataSupplier(secondsPerCandle, tradePair).getSupportedGranularities().stream()
//                    .min(Comparator.comparingInt(i -> (int) Math.abs(i - idealGranularity)))
//                    .orElseThrow(() -> new NoSuchElementException("Supported granularities was empty!"));
//            // TODO: If actualGranularity = secondsPerCandle there are no sub-candles to fetch and we must get all the
//            //  data for the current live syncing candle from the raw trades method.
            String timeFrame = x + str;
            return HttpClient.newHttpClient().sendAsync(

                            HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.binance.us/api/v3/klines?symbol=" + tradePair + "&interval=" + timeFrame))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        Log.info("Binance us Response: " + response);
                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (res.isEmpty()) {
                            return Optional.empty();
                        }

                        JsonNode currCandle;
                        Iterator<JsonNode> candleItr = res.iterator();
                        int currentTill = -1;
                        double openPrice = -1;
                        double highSoFar = -1;
                        double lowSoFar = Double.MAX_VALUE;
                        double volumeSoFar = 0;
                        double lastTradePrice = -1;
                        boolean foundFirst = false;
                        while (candleItr.hasNext()) {
                            currCandle = candleItr.next();
                            if (currCandle.get(0).asInt() < currentCandleStartedAt.getEpochSecond() ||
                                    currCandle.get(0).asInt() >= currentCandleStartedAt.getEpochSecond() +
                                            secondsPerCandle) {
                                // skip this sub-candle if it is not in the parent candle's duration (this is just a
                                // sanity guard) TODO(mike): Consider making this a "break;" once we understand why
                                //  Coinbase is  not respecting start/end times

                                currentTill = currCandle.get(0).asInt();
                                lastTradePrice = currCandle.get(4).asDouble();
                                foundFirst = true;

                                continue;

                            } else {
                                if (!foundFirst) {
                                    // FIXME: Why are we only using the first sub-candle here?
                                    currentTill = currCandle.get(0).asInt();
                                    lastTradePrice = currCandle.get(4).asDouble();
                                    foundFirst = true;
                                }
                            }

                            openPrice = currCandle.get(1).asDouble();

                            if (currCandle.get(2).asDouble() > highSoFar) {
                                highSoFar = currCandle.get(2).asDouble();
                            }

                            if (currCandle.get(3).asDouble() < lowSoFar) {
                                lowSoFar = currCandle.get(3).asDouble();
                            }

                            volumeSoFar += currCandle.get(5).asDouble();
                        }

                        int openTime = (int) (currentCandleStartedAt.toEpochMilli() / 1000L);

                        return Optional.of(new InProgressCandleData(openTime, openPrice, highSoFar, lowSoFar,
                                currentTill, lastTradePrice, volumeSoFar));
                    });

        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {

        }

        @Override
        public Future<List<CandleData>> get() {
            return null;
        }

        public static class BinanceUCandleDataSupplier extends CandleDataSupplier {
            private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
            private static final Set<Integer> GRANULARITIES = Set.of(60, 60 * 5, 60 * 15, 60 * 30, 3600, 3600 * 2, 3600 * 3, 3600 * 4, 3600 * 6, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4, 3600 * 24 * 365);

            BinanceUCandleDataSupplier(int secondsPerCandle, String tradePair) {
                super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
            }

            @Override
            public Set<Integer> getSupportedGranularities() {
                return new TreeSet<>(GRANULARITIES);
            }

            @Override
            public Future<List<CandleData>> get() {
                if (endTime.get() == -1) {
                    endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
                }

                String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        .format(LocalDateTime.ofEpochSecond(endTime.get(), 0, ZoneOffset.UTC));
                int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);
                String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        .format(LocalDateTime.ofEpochSecond(startTime, 0, ZoneOffset.UTC));

//
                Log.info("Start date: " + startDateString)
                ;//
//                ;
                Log.info("End date: " + endDateString);

                Log.info("TradePair " + tradePair);
                Log.info("Second per Candle: " + secondsPerCandle);

                if (secondsPerCandle < 3600) {
                    x = String.valueOf(secondsPerCandle / 60);
                    str = "m";
                } else if (secondsPerCandle < 86400) {
                    x = String.valueOf((secondsPerCandle / 3600));
                    str = "h";
                } else if (secondsPerCandle < 604800) {
                    x = String.valueOf(secondsPerCandle / 86400);
                    str = "d";
                } else if (secondsPerCandle < 2592000) {
                    x = String.valueOf((secondsPerCandle / 604800));
                    str = "w";
                } else {
                    x = String.valueOf((secondsPerCandle * 7 / 2592000 / 7));
                    str = "M";
                }
                String timeFrame = x + str;
                out.println("timeframe: " + timeFrame);
                String uriStr = "https://api.binance.us/api/v3/klines?symbol=" +
                        tradePair + "&interval=" + timeFrame;


                if (startTime == EARLIEST_DATA) {
                    // signal more data is false

                    out.println("startTime: " + startTime + " is false");
                    return CompletableFuture.completedFuture(Collections.emptyList());
                }

                return HttpClient.newHttpClient().sendAsync(
                                HttpRequest.newBuilder()
                                        .uri(URI.create(uriStr))
                                        .GET().build(),
                                HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .thenApply(response -> {
                            Log.info("Binance us Response: " + response);
                            JsonNode res;
                            try {
                                res = OBJECT_MAPPER.readTree(response);
                            } catch (JsonProcessingException ex) {
                                throw new RuntimeException(ex);
                            }

                            if (!res.isEmpty()) {
                                // Remove the current in-progress candle
                                if (res.get(0).asInt() + secondsPerCandle > endTime.get()) {
                                    ((ArrayNode) res).remove(0);
                                }


                                ArrayList<CandleData> candleData = new ArrayList<>();

                                for (JsonNode candle : res) {
                                    out.println("JSON " + candle);
                                    //        JSON [1632614400000,"42695.8400","43957.8200","40192.1600","43216.3600","1119.97070800",1632700799999,"47701882.7039",50948,"514.17724000","21953536.9128","0"]
                                    candleData.add(new CandleData(candle.get(1).asDouble(),  // open price
                                            candle.get(4).asDouble(),  // close price
                                            candle.get(2).asDouble(),  // high price
                                            candle.get(3).asDouble(),  // low price
                                            candle.get(0).asInt(),     // open time
                                            candle.get(5).asDouble())   // volume
                                    );
                                    endTime.set(candle.get(0).asInt());
                                    Log.info("Candle D binance " + candleData);
                                }
                                candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
                                return candleData;
                            } else {
                                return Collections.emptyList();
                            }

                        });
            }
        }
    }
}