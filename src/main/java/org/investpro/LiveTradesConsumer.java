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

public class LiveTradesConsumer {


    protected static final Logger logger = LoggerFactory.getLogger(LiveTradesConsumer.class);
    private final List<TradePair> tradePairs = new ArrayList<>();

    public Account account;
    List<Trade> livesTrades = new ArrayList<>();
    private boolean ready;

    protected InProgressCandle inProgressCandle;
    protected Exchange exchange;
    private List<Trade> tradeQueue = new ArrayList<>();


    public LiveTradesConsumer() {
    }

    public void acceptTrades(@NotNull List<Trade> trades) {
        // Handle incoming live trades
        if (trades.isEmpty()) {
            logger.warn("No trades received for processing.");
            return;
        }

        // Add trade data to a queue or buffer
        tradeQueue.addAll(trades); // You can use a concurrent queue if needed

        logger.info("Received {} trades for processing.", trades.size());

        // Process trade data
        for (Trade trade : tradeQueue) {
            if (isValidTrade(trade)) {
                processTrade(trade);
                logger.info("Processed trade: {}", trade);
            } else {
                logger.error("Invalid trade received: {}", trade);
            }
        }

        // Calculate statistics, perform calculations, and update relevant data structures
        updateTradeStatistics(tradeQueue);

    }

    // Helper method to validate a trade
    private boolean isValidTrade(Trade trade) {
        // Implement trade validation logic (e.g., check if trade has valid IDs, currency pair, amounts, etc.)
        return (trade != null) && (trade.getTradePair() != null && trade.getAmount() > 0);
    }

    // Process each trade
    private void processTrade(@NotNull Trade trade) {
        // Implement the logic to process the trade (e.g., record the trade, update live market prices, etc.)
        logger.debug("Processing trade: {} with amount: {} and price: {}",
                trade.getTradePair(), trade.getAmount(), trade.getPrice());

        // Step 1: Record the trade (e.g., store in database or in-memory list)
        recordTrade(trade);

        // Step 2: Update live market prices based on the trade
        updateMarketPrices(trade);

        // Step 3: Add trade to trade history
        updateTradeHistory(trade);

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
        tradePair.setBid(trade.getPrice() - 0.01); // Example bid price adjustment
        tradePair.setAsk(trade.getPrice() + 0.01); // Example ask price adjustment

        logger.info("Updated market prices for pair: {} - Bid: {}, Ask: {}",
                tradePair, tradePair.getBid(), tradePair.getAsk());
    }

    // Add the trade to the historical data for the trade pair
    private void updateTradeHistory(@NotNull Trade trade) {
        TradeHistory.addTrade(trade); // Assuming TradeHistory class exists
        logger.info("Trade added to history: {}", trade);
    }

    // Notify users or systems about the trade
    private void notifyUsers(@NotNull Trade trade) {
        // Example: Send a notification to subscribe users
        NotificationService.sendTradeNotification(trade); // Assuming NotificationService exists
        logger.info("Sent trade notification for: {}", trade);
    }

    // Update trade statistics based on the trade data
    private void updateTradeStatistics(@NotNull List<Trade> tradeQueue) {
        // Calculate statistics like volume, average trade price, etc.
        double totalVolume = tradeQueue.stream()
                .mapToDouble(Trade::getAmount)
                .sum();
        double avgPrice = tradeQueue.stream()
                .mapToDouble(Trade::getPrice)
                .average()
                .orElse(0);

        logger.info("Trade volume: {}, Average trade price: {}", totalVolume, avgPrice);
        // Update your trade statistics data structure (e.g., database, in-memory store)
    }









    public void clear() {
        // Clear live trade processing data structure (e.g., database, in-memory store)
        this.tradePairs.clear();

        this.exchange.clear();
        this.inProgressCandle = null;
        this.ready = false;

    }

    public void add(Exchange exchange) {

        this.exchange.add(exchange);
    }

    public @NotNull List<Trade> getLiveTrades() {

        return livesTrades;// Placeholder for actual implementation
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
}
