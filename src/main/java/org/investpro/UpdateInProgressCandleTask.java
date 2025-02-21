package org.investpro;

import javafx.application.Platform;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Getter
@Setter
public class UpdateInProgressCandleTask extends LiveTradesConsumer implements Runnable {
    private final BlockingQueue<Trade> liveTradesQueue;

    private boolean ready;
    private List<CandleData> data;
    private TradePair tradePair;
    private long secondsPerCandle;

    UpdateInProgressCandleTask(Exchange exchange, int secondsPerCandle, TradePair tradePair, List<CandleData> data) {
        this.secondsPerCandle = secondsPerCandle;
        this.tradePair = tradePair;
        this.exchange = exchange;
        this.data = data;
        liveTradesQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void acceptTrades(@NotNull List<Trade> trades) {
        liveTradesQueue.addAll(trades);
    }

    @Override
    public void run() {
        if (!ready) {
            ready = true;
            Platform.runLater(this);
            return;
        }

        int currentTill = (int) Instant.now().getEpochSecond();

        Consumer<List<Trade>> liveTradesConsumer = (trades -> Platform.runLater(() -> acceptTrades(trades)));


        // Fetch recent trades from the exchange
        List<Trade> liveTrades = new ArrayList<>();


        exchange.fetchRecentTradesUntil(tradePair, Instant.now(), liveTradesConsumer);
            liveTradesQueue.drainTo(liveTrades);


        if (liveTrades.isEmpty()) {
            logger.warn("No new trades available.");
            return;
        }

        // Filter new trades occurring after the last known trade timestamp
        List<Trade> newTrades = liveTrades.stream()
                .filter(trade -> trade.getTimestamp().getEpochSecond() > inProgressCandle.getCurrentTill())
                .toList();

        // Partition trades between current in-progress candle and the next candle
        Map<Boolean, List<Trade>> candlePartitionedNewTrades = newTrades.stream()
                .collect(Collectors.partitioningBy(
                        trade -> trade.getTimestamp().getEpochSecond() < inProgressCandle.getOpenTime() + secondsPerCandle));

        List<Trade> currentCandleTrades = candlePartitionedNewTrades.get(true);
        List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(false);

        // Update the in-progress candle
        if (!currentCandleTrades.isEmpty()) {
            double maxPrice = currentCandleTrades.stream().mapToDouble(Trade::getPrice).max().orElse(inProgressCandle.getHighPriceSoFar());
            double minPrice = currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().orElse(inProgressCandle.getLowPriceSoFar());
            double volumeSum = currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum();
            double closePrice = currentCandleTrades.get(currentCandleTrades.size() - 1).getPrice();

            inProgressCandle.setHighPriceSoFar(Math.max(maxPrice, inProgressCandle.getHighPriceSoFar()));
            inProgressCandle.setLowPriceSoFar(Math.min(minPrice, inProgressCandle.getLowPriceSoFar()));
            inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() + volumeSum);
            inProgressCandle.setClosePriceSoFar(closePrice);
            inProgressCandle.setCurrentTill(currentTill);

            data.add((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
        } else {
            logger.info("No trades updated in the current candle.");

            return;
        }

        // Handle new candle formation
        if (currentTill >= inProgressCandle.getOpenTime() + secondsPerCandle) {
            inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);

            if (!nextCandleTrades.isEmpty()) {
                Trade firstTrade = nextCandleTrades.getFirst();

                inProgressCandle.setIsPlaceholder(false);
                inProgressCandle.setOpenPrice(firstTrade.getPrice());
                inProgressCandle.setHighPriceSoFar(firstTrade.getPrice());
                inProgressCandle.setLowPriceSoFar(firstTrade.getPrice());
                inProgressCandle.setVolumeSoFar(firstTrade.getAmount());
                inProgressCandle.setClosePriceSoFar(firstTrade.getPrice());
                inProgressCandle.setCurrentTill((int) firstTrade.getTimestamp().getEpochSecond());

                logger.info("New candle created with first trade at price: {}", firstTrade.getPrice());
            } else {
                // Placeholder candle (no new trades in this period)
                inProgressCandle.setIsPlaceholder(true);
                inProgressCandle.setHighPriceSoFar(inProgressCandle.getClosePriceSoFar());
                inProgressCandle.setLowPriceSoFar(inProgressCandle.getLowPriceSoFar());
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar());

                logger.warn("New candle created as a placeholder (no trades in this period).");
            }

            data.add((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
        }

        Platform.runLater(this);
    }
}
