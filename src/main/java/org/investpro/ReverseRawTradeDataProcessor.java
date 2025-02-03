package org.investpro;

import javafx.beans.property.SimpleIntegerProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.investpro.exchanges.Oanda.numCandles;

/**
 * @author NOEL NGUEMECHIEU
 */

public class ReverseRawTradeDataProcessor extends CandleDataSupplier {
    private final ReversedLinesFileReader fileReader;
    private int start;
    private static final Logger logger = LoggerFactory.getLogger(ReverseRawTradeDataProcessor.class);

    public ReverseRawTradeDataProcessor(Path rawTradeData, int secondsPerCandle, TradePair tradePair)
            throws IOException {
        super(secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        fileReader = new ReversedLinesFileReader(rawTradeData, StandardCharsets.UTF_8);
    }

    @Override
    public Future<List<CandleData>> get() {
        final Map<Integer, TreeSet<Trade>> candleTrades = new ConcurrentHashMap<>(numCandles);

        String line;
        try {
            while ((line = fileReader.readLine()) != null) {
                String[] commaSplitLine = line.split(",");
                if (commaSplitLine.length != 3) {
                    throw new IllegalArgumentException("raw trade data malformed");
                }

                final int timestamp = Integer.parseInt(commaSplitLine[0]);

                if (endTime.get() == -1) {
                    start = timestamp;
                    endTime.set(start - (secondsPerCandle * numCandles));
                }

                if (timestamp < endTime.get()) {
                    logger.debug(
                            "Skipping trade at timestamp {} outside of requested range: [{}, {}]",
                            timestamp, start, endTime.get()
                    );
                    break;
                }

                Trade trade = new Trade();

                int candleIndex = (numCandles - ((start - timestamp) / secondsPerCandle)) - 1;
                if (candleTrades.get(candleIndex) == null) {
                    // noinspection Convert2Diamond
                    candleTrades.put(candleIndex, new TreeSet<Trade>((t1, t2) ->
                            Integer.compare(t2.getTimestamp().getNano(), t1.getTimestamp().getNano())));
                } else {
                    candleTrades.get(candleIndex).add(trade);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        final List<CandleData> candleData = new ArrayList<>(numCandles);
        double lastClose = -1;
        for (int i = 0; i < numCandles; i++) {
            int openTime = (start - secondsPerCandle) - (i * secondsPerCandle);
            double volume = i;
            if (candleTrades.get(i) == null || candleTrades.get(i).isEmpty()) {
                // no trades occurred during this candle
                candleData.add(new CandleData(lastClose, lastClose, lastClose, lastClose, openTime, 0, volume));
            } else {
                double open = 0;
                double high = -1;
                double low = Double.MAX_VALUE;
                double close = 0;
                volume = 0;
                double priceTotal = 0;
                double volumeWeightedPriceTotal = 0;
                int tradeIndex = 0;
                for (Trade trade : candleTrades.get(i)) {
                    if (tradeIndex == 0) {
                        open = trade.getPrice();
                    }

                    if (trade.getPrice() > high) {
                        high = trade.getPrice();
                    }

                    if (trade.getPrice() < low) {
                        low = trade.getPrice();
                    }

                    if (tradeIndex == candleTrades.get(i).size() - 1) {
                        close = trade.getPrice();
                    }

                    priceTotal += trade.getPrice();
                    volumeWeightedPriceTotal += trade.getPrice() * trade.getAmount();
                    volume += trade.getAmount();
                    tradeIndex++;
                    lastClose = close;
                }

                double averagePrice = priceTotal / candleTrades.get(i).size();
                double volumeWeightedAveragePrice = volumeWeightedPriceTotal / volume;

                CandleData datum = new CandleData(open, close, Math.max(open, high), Math.min(open, low), openTime,
                        0, volume);
                candleData.add(datum);
            }
        }

        start = endTime.get();
        endTime.set(endTime.get() - (secondsPerCandle * numCandles));
        return CompletableFuture.completedFuture(candleData.stream().sorted(Comparator.comparingLong(
                CandleData::getOpenTime)).collect(Collectors.toList()));
    }


    @Override
    public Set<Integer> getSupportedGranularity() {
        return Set.of(
                // Supported granularity
                60, 300, 3600, 4*3600, 6*3600, 12*3600
        );
    }

    /**
     * This method returns an instance of CandleDataSupplier based on the provided parameters.
     *
     * @param secondsPerCandle The duration of each candle in seconds.
     * @param tradePair        The trade pair for which the candle data is necessary.
     * @return An instance of CandleDataSupplier or null if no appropriate supplier is found.
     */
    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, @NotNull TradePair tradePair) {
        // Validate input
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("Invalid parameters for CandleDataSupplier");
        }

//        // Example: Choose the supplier based on candle duration and a trade pair
//        if (secondsPerCandle == 60) {
//            // Return a 1-minute candle data supplier
//            return new OneMinuteCandleDataSupplier(tradePair);
//        } else if (secondsPerCandle == 300) {
//            // Return a 5-minute candle data supplier
//            return new FiveMinuteCandleDataSupplier(tradePair);
//        } else if (secondsPerCandle == 3600) {
//            // Return a 1-hour candle data supplier
//            return new OneHourCandleDataSupplier(tradePair);
//        } else {
//            // Handle other durations or throw an exception
//            logger.error("Unsupported candle duration: {} seconds", secondsPerCandle);
//            return null; // or throw new UnsupportedOperationException("Unsupported candle duration");
//        }
        return null; // or throw new UnsupportedOperationException("Unsupported candle duration");
    }


}
