package org.investpro.data;

import lombok.extern.slf4j.Slf4j;

import javafx.beans.property.SimpleIntegerProperty;
import lombok.Getter;
import lombok.Setter;
import  org.investpro.exchange.Exchange;
import  org.investpro.models.trading.Trade;
import  org.investpro.models.trading.TradePair;
import  org.investpro.utils.CandleDataSupplier;
import  org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Setter
@Getter
@Slf4j
public class ReverseRawTradeDataProcessor extends CandleDataSupplier {
    private final Path rawTradeData;
    private final Exchange exchange;
    private int start;

    public ReverseRawTradeDataProcessor(Path rawTradeData, int secondsPerCandle, TradePair tradePair, Exchange exchange) throws IOException {
        super(300, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        this.rawTradeData = Objects.requireNonNull(rawTradeData, "rawTradeData must not be null");
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");

        log.info("Reading raw trade data from {}", rawTradeData);
    }

    @Override
    public Future<List<CandleData>> get() {
        Map<Integer, TreeSet<Trade>> candleTrades = new HashMap<>(numCandles);
        long tradeId = 0;

        try (ReversedLinesFileReader fileReader = new ReversedLinesFileReader(rawTradeData, StandardCharsets.UTF_8)) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 3) {
                    log.debug("Skipping malformed raw trade line: {}", line);
                    continue;
                }

                int timestamp = Integer.parseInt(parts[0].trim());
                double price = Double.parseDouble(parts[1].trim());
                double amount = Double.parseDouble(parts[2].trim());

                if (endTime.get() == -1) {
                    start = timestamp;
                    endTime.set(start - (secondsPerCandle * numCandles));
                } else if (start <= 0) {
                    start = endTime.get() + (secondsPerCandle * numCandles);
                }

                if (timestamp < endTime.get()) {
                    break;
                }
                if (timestamp > start) {
                    continue;
                }

                int candleIndex = (numCandles - ((start - timestamp) / secondsPerCandle)) - 1;
                if (candleIndex < 0 || candleIndex >= numCandles) {
                    continue;
                }

                Trade trade = new Trade(
                        tradePair,
                        price,
                        amount,
                        Side.SELL,
                        ++tradeId,
                        Instant.ofEpochSecond(timestamp)
                );

                candleTrades.computeIfAbsent(candleIndex, ignored -> new TreeSet<>(
                        Comparator.comparing(Trade::getTimestamp)
                                .thenComparingLong(Trade::getLocalTradeId)
                )).add(trade);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (RuntimeException exception) {
            throw new RuntimeException("Unable to parse raw trade data from %s".formatted(rawTradeData), exception);
        }

        List<CandleData> candleData = new ArrayList<>(numCandles);
        double lastClose = 0.0;
        for (int i = 0; i < numCandles; i++) {
            int openTime = (start - (secondsPerCandle * numCandles)) + (i * secondsPerCandle);
            TreeSet<Trade> trades = candleTrades.get(i);
            if (trades == null || trades.isEmpty()) {
                candleData.add(new CandleData(lastClose, lastClose, lastClose, lastClose, openTime, 0.0, lastClose, lastClose, true));
                continue;
            }

            double open = trades.first().getPrice();
            double close = trades.last().getPrice();
            double high = open;
            double low = open;
            double volume = 0.0;
            double priceTotal = 0.0;
            double volumeWeightedPriceTotal = 0.0;

            for (Trade trade : trades) {
                double price = trade.getPrice();
                double amount = trade.getAmount();
                high = Math.max(high, price);
                low = Math.min(low, price);
                volume += amount;
                priceTotal += price;
                volumeWeightedPriceTotal += price * amount;
            }

            double averagePrice = priceTotal / trades.size();
            double volumeWeightedAveragePrice = volume > 0.0 ? volumeWeightedPriceTotal / volume : averagePrice;
            candleData.add(new CandleData(open, close, high, low, openTime, volume, averagePrice, volumeWeightedAveragePrice, false));
            lastClose = close;
        }

        start = endTime.get();
        endTime.set(endTime.get() - (secondsPerCandle * numCandles));
        return CompletableFuture.completedFuture(candleData);
    }

    @Override
    public List<CandleData> getCandleData() {
        try {
            return get().get();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load candle data from raw trades", exception);
        }
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        try {
            return new ReverseRawTradeDataProcessor(rawTradeData, secondsPerCandle, tradePair, exchange);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create raw trade data supplier", exception);
        }
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        List<Trade> trades = new ArrayList<>();
        long tradeId = 0;

        try (ReversedLinesFileReader fileReader = new ReversedLinesFileReader(rawTradeData, StandardCharsets.UTF_8)) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 3) {
                    continue;
                }

                int timestamp = Integer.parseInt(parts[0].trim());
                Instant instant = Instant.ofEpochSecond(timestamp);
                if (stopAt != null && instant.isBefore(stopAt)) {
                    break;
                }

                trades.add(new Trade(
                        tradePair == null ? this.tradePair : tradePair,
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Side.SELL,
                        ++tradeId,
                        instant
                ));
            }
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        trades.sort(Comparator.comparing(Trade::getTimestamp));
        return CompletableFuture.completedFuture(trades);
    }
}
