package org.investpro.investpro.oanda;

import javafx.application.Platform;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import org.investpro.investpro.*;

import java.io.IOException;
import java.net.URISyntaxException;

public class OandaClient {

    private static final String BTC_USD = "AUD_USD";

    public static void createMarketOrder(String tradePair, String market, String side, int size) {
    }


    public OandaContainer start() throws URISyntaxException, IOException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: \n" + exception));
        OandaContainer candleStickChartContainer =
                new OandaContainer(new Oanda("https://api-fxtrade.oanda.com/", Oanda.getApi_key(), Oanda.getAccountID()), BTC_USD, true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }

//        @Override
//        public Future<List<CandleData>> get() {
//            if (endTime.get() == -1) {
//                endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
//                out.println("End time " + endTime);
//            }
//
//            String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
//                    .format(LocalDateTime.ofEpochSecond(endTime.get(), 0, ZoneOffset.UTC));
//
//            final int[] startTime = {Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA)};
//            String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
//                    .format(LocalDateTime.ofEpochSecond(startTime[0], 0, ZoneOffset.UTC));
//
////
//            Log.info("Start date: " + startDateString)
//            ;//
////                ;
//            Log.info("End date: " + endDateString);
//
//            Log.info("TradePair " + String.valueOf(tradePair
//            ).replace("/", ""));
//            Log.info("Second per Candle: " + secondsPerCandle);
//            String x, str;
//            if (secondsPerCandle < 3600) {
//                x = String.valueOf(secondsPerCandle / 60);
//                str = "M";
//            } else if (secondsPerCandle < 86400) {
//                x = String.valueOf((secondsPerCandle / 3600));
//                str = "H";
//            } else if (secondsPerCandle < 604800) {
//                x = "";//String.valueOf(secondsPerCandle / 86400);
//                str = "D";
//            } else if (secondsPerCandle < 2592000) {
//                x = String.valueOf((secondsPerCandle / 604800));
//                str = "W";
//            } else {
//                x = String.valueOf((secondsPerCandle * 7 / 2592000 / 7));
//                str = "M";
//            }
//
//            String granularity = str + x;
//            String uriStr = "https://api-fxtrade.oanda.com/v3/instruments/" + tradePair.toString('_') + "/candles?price=BA&from=2016-10-17T15%3A00%3A00.000000000Z&granularity=" + granularity;
//
//
//            //String.format("https://api-fxtrade.oanda.com/v3/instruments/" + tradePair.toString('_') + "/candles?count=10&price=M&from=2016-10-17T15%3A00%3A00.000000000Z&granularity=" + actualGranularity);
//
//            out.println("timeframe: " + granularity);
//
//
//            if (startTime[0] == EARLIEST_DATA) {
//                // signal more data is false
//                return CompletableFuture.completedFuture(Collections.emptyList());
//            }
//
//            HttpRequest.Builder req = HttpRequest.newBuilder();
//            req.uri(URI.create(uriStr));
//            req.header("Authorization", "Bearer " + OandaClient.getApi_key());
//            return HttpClient.newHttpClient().sendAsync(
//                            req.build(),
//                            HttpResponse.BodyHandlers.ofString())
//                    .thenApply(HttpResponse::body)
//                    .thenApply(response -> {
//                        Log.info("Oanda us Response: " + response);
//
//                        double volume, o = 0, c = 0, h = 0, l = 0;
//
//
////
//                        if (!response.isEmpty()) {
//                            GregorianCalendar d;
//
//                            JSONObject cand0 = new JSONObject(response);
//                            for (int i = 0; i < cand0.length(); i++) {
//
//                                // Remove the current in-progress candl
//                                //
//                                JSONObject cand = (JSONObject) cand0.getJSONArray("candles").get(i);
//
//                                if (cand.has("candles")) {
//
//
//                                    JSONArray candles = cand.getJSONArray("candles");
//
//
//                                    String time;
//
//                                    time = candles.getJSONObject(i).get("time").toString();
//                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//
//
//                                    d = GregorianCalendar.from(ZonedDateTime.parse(time));
//
//                                    endTime.set((int) d.getTime().getTime());
//
//                                    //       JSONObject json = new JSONObject(response);
//
//
//                                    volume = Double.parseDouble(candles.getJSONObject(i).get("volume").toString());
//
//                                    if (candles.getJSONObject(i).has("bid")) {
//
//                                        JSONObject bid = candles.getJSONObject(i).getJSONObject("bid");
//
//                                        if (bid.has("o")) {
//
//                                            o = bid.getDouble("o");
//                                        }
//                                        if (bid.has("c")) {
//                                            c = bid.getDouble("c");
//                                        }
//                                        if (bid.has("h")) {
//                                            h = bid.getDouble("h");
//                                        }
//                                        if (bid.has("l")) {
//                                            l = bid.getDouble("l");
//                                        }
////
//
//                                        List<CandleData> candleData = new ArrayList<>();
//
//                                        candleData.add(new CandleData(o, c, h, l, (int) d.getTime().getTime(), volume));
////
////
//                                        out.println("My candle data =>" + candleData);
//
//
//                                        return candleData;
//
//                                    }
//                                }
//                            }
//                        } else {
//                            return Collections.emptyList();
//                        }
//                        return null;
//                    });


    /**
     * A {@link Region} that contains a {@code CandleStickChart} and a {@code CandleStickChartToolbar}.
     * The contained chart will display data for the given {@code tradePair}. The toolbar allows for changing
     * the duration in seconds of each candle as well as configuring the properties of the chart. When a new
     * duration is selected, this container automatically creates a new {@code CandleStickChart} and visually
     * transitions to it.
     *
     * @author noel martial nguemechieu
     */

}


//
//S5	5 second candlesticks, minute alignment
//        S10	10 second candlesticks, minute alignment
//        S15	15 second candlesticks, minute alignment
//        S30	30 second candlesticks, minute alignment
//        M1	1 minute candlesticks, minute alignment
//        M2	2 minute candlesticks, hour alignment
//        M4	4 minute candlesticks, hour alignment
//        M5	5 minute candlesticks, hour alignment
//        M10	10 minute candlesticks, hour alignment
//        M15	15 minute candlesticks, hour alignment
//        M30	30 minute candlesticks, hour alignment
//        H1	1 hour candlesticks, hour alignment
//        H2	2 hour candlesticks, day alignment
//        H3	3 hour candlesticks, day alignment
//        H4	4 hour candlesticks, day alignment
//        H6	6 hour candlesticks, day alignment
//        H8	8 hour candlesticks, day alignment
//        H12	12 hour candlesticks, day alignment
//        D	1 day candlesticks, day alignment
//        W	1 week candlesticks, aligned to start of week
//        M