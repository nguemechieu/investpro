package org.investpro;

import javafx.beans.property.SimpleIntegerProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;


public class ReverseRawTradeDataProcessor extends CandleDataSupplier {
    private final ReversedLinesFileReader fileReader;
    private static final Logger logger = LoggerFactory.getLogger(ReverseRawTradeDataProcessor.class);
    int tradeIndex = 1;
    double volumeWeightedPriceTotal;
    Exchange exchange;
    String line;
    private int start;
    public ReverseRawTradeDataProcessor(Path rawTradeData, int secondsPerCandle, TradePair tradePair, Exchange exchange) throws IOException {

        super(300, secondsPerCandle, tradePair, new SimpleIntegerProperty(secondsPerCandle));
        fileReader = new ReversedLinesFileReader(rawTradeData, StandardCharsets.UTF_8);
        this.exchange = exchange;

        logger.info("Reading raw trade data from {}", rawTradeData);
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Future<List<CandleData>> get() {
        final Map<Integer, TreeSet<Trade>> candleTrades = new HashMap<>(numCandles);


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
                } else

                if (timestamp < endTime.get()) {
                    break;
                }


                long trade_id = 0;
                trade_id++;
                Trade trade = new Trade(tradePair,
                        DefaultMoney.of(BigDecimal.valueOf(exchange.getLivePrice()),
                                exchange.getSelecTradePair().counterCurrency),
                        DefaultMoney.of(BigDecimal.valueOf(exchange.getSize()),
                                exchange.getSelecTradePair().baseCurrency),
                        Side.SELL, trade_id
                        ,
                        Instant.ofEpochSecond(new Date().getTime()));



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
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        double open = 0;
        double high = -1;
        double low = Double.MAX_VALUE;
        double close = 0;
        double volume = 0;
        double priceTotal = 0;
        final List<CandleData> candleData = new ArrayList<>(numCandles);
        double lastClose = -1;
        for (int i = 0; i < numCandles; i++) {
            int openTime = (start - secondsPerCandle) - (i * secondsPerCandle);
            if (candleTrades.get(i) == null || candleTrades.get(i).isEmpty()) {
                // no trades occurred during this candle
                double volumeWeightedAveragePrice1 = (lastClose == -1) ? 0 : (lastClose - openTime) / volume;
                candleData.add(new CandleData(lastClose, lastClose, lastClose, lastClose, openTime, volume, volumeWeightedAveragePrice1, volumeWeightedAveragePrice1, true));

            } else {


                for (Trade trade : candleTrades.get(i)) {
                    if (tradeIndex == 0) {
                        open = trade.getPrice().toDouble();
                    } else if (trade.getPrice().toDouble() > high) {
                        high = trade.getPrice().toDouble();
                    } else if (trade.getPrice().toDouble() < low) {
                        low = trade.getPrice().toDouble();
                    } else

                    if (tradeIndex == candleTrades.get(i).size() - 1) {
                        close = trade.getPrice().toDouble();
                    }

                    priceTotal += trade.getPrice().toDouble();
                    volumeWeightedPriceTotal += trade.getPrice().toDouble() * trade.getAmount().toDouble();
                    volume += trade.getAmount().toDouble();
                    tradeIndex++;
                    lastClose = close;
                }

                double averagePrice = priceTotal / candleTrades.get(i).size();
                double volumeWeightedAveragePrice = volumeWeightedPriceTotal / volume;

                CandleData datum = new CandleData(open, close, Math.max(open, high), Math.min(open, low), openTime,
                        volume, averagePrice, volumeWeightedAveragePrice, false);
                candleData.add(datum);
            }
        }

        start = endTime.get();
        endTime.set(endTime.get() - (secondsPerCandle * numCandles));
        return CompletableFuture.completedFuture(candleData.stream().sorted(Comparator.comparingInt(
                CandleData::getOpenTime)).collect(Collectors.toList()));
    }

    @Override
    public List<CandleData> getCandleData() {
        return null;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return null;
    }
}
