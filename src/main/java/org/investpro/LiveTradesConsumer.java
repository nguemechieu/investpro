package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter

public  class LiveTradesConsumer {


    protected static final Logger logger = LoggerFactory.getLogger(LiveTradesConsumer.class);
    private final List<TradePair> tradePairs = new ArrayList<>();

    public Account account;
    List<Trade> livesTrades = new ArrayList<>(

    );
    private boolean ready;

    protected InProgressCandle inProgressCandle;
    protected Exchange exchange;
    private List<Trade> tradeQueue = new ArrayList<>();


    public LiveTradesConsumer() {
    }



    // Process each trade
    public void processTrade(@NotNull Trade trade) {
        // Implement the logic to process the trade (e.g., record the trade, update live market prices, etc.)
        logger.debug("Processing trade: {} with amount: {} and price: {}",
                trade.getTradePair(), trade.getAmount(), trade.getPrice());

        // Step 1: Record the trade (e.g., store in database or in-memory list)
        recordTrade(trade);

        // Step 2: Update live market prices based on the trade
        updateMarketPrices(trade);

        // Step 3: Add trade to trade history
        //updateTradeHistory();

        // Step 4: Send notifications (if applicable)
        notifyUsers(trade);

        logger.info("Trade processed successfully for pair: {}", trade.getTradePair());
    }

    // Record the trade in a data store (e.g., database or in-memory list)
    private void recordTrade(@NotNull Trade trade) {
        // For simplicity, assume an in-memory trade storage
        TradeStorage.addTrade(trade); // Assuming a TradeStorage class exists
        logger.info("Recorded trade: {}", trade);
    }

    // Update market prices (bid/ask) for the trade pair based on the trade data
    private void updateMarketPrices(@NotNull Trade trade) {
        TradePair tradePair = trade.getTradePair();

        // Simulate updating bid and ask prices based on the trade
        tradePair.setBid(trade.getPrice().byteValueExact() - 0.01); // Example bid price adjustment
        tradePair.setAsk(trade.getPrice().byteValueExact() + 0.01); // Example ask price adjustment

        logger.info("Updated market prices for pair: {} - Bid: {}, Ask: {}",
                tradePair, tradePair.getBid(), tradePair.getAsk());
    }

    // Add the trade to the historical data for the trade pair
    private void updateTradeHistory(List<CandleData> trade) {
        TradeHistory.addTrade((Trade) trade); // Assuming TradeHistory class exists
        logger.info("Trade added to history: {}", trade);
    }

    // Notify users or systems about the trade
    private void notifyUsers(@NotNull Trade trade) {
        // Example: Send a notification to subscribe users
        NotificationService.sendTradeNotification(trade); // Assuming NotificationService exists
        logger.info("Sent trade notification for: {}", trade);
        // You can also notify systems about the trade using other communication channels (e.g., email, SMS, etc.)
    }

    // Update trade statistics based on the trade data
    private void updateTradeStatistics(@NotNull List<Trade> tradeQueue) {
        // Calculate statistics like volume, average trade price, etc.
        double totalVolume = tradeQueue.stream()
                .mapToDouble(m->m.getAmount().doubleValue())
                .sum();
        double avgPrice = tradeQueue.stream()
                .mapToDouble(m->m.getAmount().doubleValue())
                .average()
                .orElse(0);

        logger.info("Trade volume: {}, Average trade price: {}", totalVolume, avgPrice);
        // Update your trade statistics data structure (e.g., database, in-memory store)
    }









    public void clear() {
        // Clear live trade processing data structure (e.g., database, in-memory store)
        this.tradePairs.clear();

        this.exchange.clear();
        this.inProgressCandle = new InProgressCandle();
        this.ready = false;

    }




    public void accept(Trade liveTrade) {
        // Process live trade
        processTrade(liveTrade);
        // Update trade statistics, portfolio, and notify users
        updateTradeStatistics(this.tradeQueue);

        notifyUsers(liveTrade);
    }



    public Collection<? extends Trade> get(TradePair tradePair) {

        ArrayList<Trade> trades = new ArrayList<>();
        for (Trade trade : tradeQueue) {
            if (trade.getTradePair().equals(tradePair)) {
                trades.add(trade);
            }
        }
        return trades;
    }

    public void accept(List<CandleData> candleData) {


        if (candleData.isEmpty()) {
            logger.info("No candle data received on LiveTradesConsumer");
            return; // No candle data to process
        }
        // Implement the logic to process the candle data (e.g., update the in-progress candle, etc.)
        logger.debug("Processing candle data: {}", candleData);

        // Step 1: Update the in-progress candle based on the new candle data
        updateInProgressCandle(candleData);

        // Step 2: Add the candle data to the trade history
        updateTradeHistory(candleData);

        // Step 3: Send notifications (if applicable)
        notifyUsers((Trade) candleData);

        logger.info("Candle data processed successfully for pair: {}", candleData.getFirst());

    }

    private void updateInProgressCandle(@NotNull List<CandleData> candleData) {
        // Implement the logic to update the in-progress candle based on the new candle data
        // For simplicity, assume the in-progress candle is stored in a data structure
        this.inProgressCandle = candleData.getLast().getSnapshot();
    }
}
