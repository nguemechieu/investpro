package org.investpro;

import javafx.beans.property.SimpleIntegerProperty;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


public class ReverseRawTradeDataProcessor extends CandleDataSupplier {
    private static final Logger logger = LoggerFactory.getLogger(ReverseRawTradeDataProcessor.class);
    private final SimpleIntegerProperty endTime = new SimpleIntegerProperty(-1);
    private final ReversedLinesFileReader fileReader;
    private int start;
    private int count;

    public ReverseRawTradeDataProcessor(Path rawTradeData, int secondsPerCandle, TradePair tradePair)
            throws IOException {
        super(secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        fileReader = new ReversedLinesFileReader(rawTradeData, StandardCharsets.UTF_8);
    }

    @Override
    public Future<List<CandleData>> get() {
        int numCandles = 1000;
        final Map<Integer, TreeSet<CandleData>> candleTrades = new HashMap<>(numCandles - 1);
        String line;

        try {
            while ((line = fileReader.readLine()) != null) {
                line = line.trim(); // Trim whitespace
                if (line.isEmpty()) continue; // Skip empty lines

                String[] commaSplitLine = line.split(",");
                if (commaSplitLine.length != 6) { // Expecting 6 values
                    logger.error("Malformed trade data: {}", line); // Log issue
                    continue; // Skip malformed line instead of failing
                }

                try {

                    if (count == 1) {
                        logger.info("Skipping first trade data line: {}", line);
                        continue;
                    }
                    logger.info("Trade data timestamp: {}", commaSplitLine[0]);
                    count++;
                    Date date;//
                    date = Date.from(Instant.parse(commaSplitLine[0].trim()));
                    int timestamp = (int) date.toInstant().getEpochSecond();
                    final double open = Double.parseDouble(commaSplitLine[1].trim());
                    final double high = Double.parseDouble(commaSplitLine[2].trim());
                    final double low = Double.parseDouble(commaSplitLine[3].trim());
                    final double close = Double.parseDouble(commaSplitLine[4].trim());
                    final double volume = Double.parseDouble(commaSplitLine[5].trim());

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

                    CandleData candle = new CandleData(open, close, high, low, timestamp, 0, volume);
                    int candleIndex = (numCandles - ((start - timestamp) / secondsPerCandle)) - 1;

                    candleTrades.computeIfAbsent(candleIndex, _ -> new TreeSet<>(Comparator.comparingInt(CandleData::getOpenTime).reversed()))
                            .add(candle);

                } catch (NumberFormatException | DateTimeParseException e) {
                    logger.error("Invalid number format in line: {}", line, e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading trade data file", e);
        }

        final List<CandleData> candleData = new ArrayList<>(numCandles);
        double lastClose = -1;

        for (int i = 0; i < numCandles; i++) {
            int openTime = (start - secondsPerCandle) - (i * secondsPerCandle);

            if (!candleTrades.containsKey(i) || candleTrades.get(i).isEmpty()) {
                // No trades occurred during this candle
                candleData.add(new CandleData(lastClose, lastClose, lastClose, lastClose, openTime, 0, 0));
            } else {
                double open = 0, high = -1, low = Double.POSITIVE_INFINITY, close = 0, volume = 0;
                double priceTotal = 0, volumeWeightedPriceTotal = 0;
                int tradeIndex = 0;

                for (CandleData trade : candleTrades.get(i)) {
                    if (tradeIndex == 0) open = trade.getOpenPrice();
                    high = Math.max(high, trade.getHighPrice());
                    low = Math.min(low, trade.getLowPrice());
                    if (tradeIndex == candleTrades.get(i).size() - 1) close = trade.getClosePrice();

                    priceTotal += trade.getClosePrice();
                    volumeWeightedPriceTotal += trade.getClosePrice() * trade.getVolume();
                    volume += trade.getVolume();
                    tradeIndex++;
                    lastClose = close;
                }

                double averagePrice = priceTotal / candleTrades.get(i).size();
                double volumeWeightedAveragePrice = volumeWeightedPriceTotal / volume;

                candleData.add(new CandleData(open, close, high, low, openTime, 0, volume));
            }
        }

        start = endTime.get();
        endTime.set(endTime.get() - (secondsPerCandle * numCandles));

        return CompletableFuture.completedFuture(
                candleData.stream()
                        .sorted(Comparator.comparingInt(CandleData::getOpenTime)).toList()
        );
    }


    @Override
    public Set<Integer> getSupportedGranularity() {
        return Set.of(
                1, // 1-minute
                5, // 5-minute
                15, // 15-minute
                30, // 30-minute
                60, // 1 hour
                120, // 2 hours
                240, // 4 hours
                480, // 8 hours
                1440 // 1 day
        );
    }

    /**
     * Represents one line of the raw trade data. We use doubles because the results don't need to be
     * *exact* (i.e., small rounding errors are fine), and we want to favor speed.
     */
    private record Trade(int timestamp, double price, double amount) {
        @Contract(pure = true)
        @Override
        public @NotNull String toString() {
            return String.format("Trade [timestamp = %d, price = %f, amount = %f]", timestamp, price, amount);
        }
    }
}
