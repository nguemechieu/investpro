package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Getter
@Setter

public class LiveTradesConsumer {


    private static final Logger logger = LoggerFactory.getLogger(LiveTradesConsumer.class);
    private final List<TradePair> tradePairs = new ArrayList<>();

    public Account account;
    List<Trade> livesTrades;
    private boolean ready;

    private InProgressCandle inProgressCandle;
    private Exchange exchange;
    private List<Trade> tradeQueue;

    public LiveTradesConsumer(Exchange exchange, @NotNull Account account) {
        this.exchange = exchange;
        // Initialize consumer with a default account
        this.account = account;
        this.inProgressCandle = new InProgressCandle();
        this.ready = false;
        this.tradeQueue = new ArrayList<>();
        this.livesTrades = new ArrayList<>();

    }


    public LiveTradesConsumer() {
    }

    public void acceptTrades(@NotNull List<Trade> trades) {
        // Handle incoming live trades
        if (trades.isEmpty()) {
            logger.warn("No trades received for processing.");
            return;
        }

        // Add trade data to a queue or buffer
        List<Trade> tradeQueue = new ArrayList<>(trades); // You can use a concurrent queue if needed

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
        double tradePrice = trade.getPrice();

        // Simulate updating bid and ask prices based on the trade
        tradePair.setBid(tradePrice - 0.01); // Example bid price adjustment
        tradePair.setAsk(tradePrice + 0.01); // Example ask price adjustment

        logger.info("Updated market prices for pair: {} - Bid: {}, Ask: {}",
                tradePair, tradePair.getBid(), tradePair.getAsk());
    }

    // Add the trade to the historical data for the trade pair
    private void updateTradeHistory(@NotNull Trade trade) {
        TradeHistory.addTradeToHistory(trade); // Assuming TradeHistory class exists
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




    public void run() {
        // Implement live trade processing
        this.acceptTrades(
                getExchange().getLiveTrades(this.tradePairs) // Get live trades from exchange and
        ); // Get live trades from exchange and

    }

    private Exchange getExchange() {

        return this.exchange; // Replace this with actual exchange implementation

    }

    public void setExchange(Exchange exchange) {
        // Set exchange for live trade processing
        if (exchange == null) {
            logger.warn("No exchange provided. Unable to set exchange for live trading.");
            return;
        }

        // Clear any existing exchange if necessary (depends on your logic)
        this.exchange.clear();

        // Implement exchange selection and processing logic
        this.exchange.add(exchange);
        logger.info("Added exchange: {} to live trading.", exchange);

        // Further processing, e.g., start listening to live market data for this exchange
        startLiveTrading(this.tradePairs);
        logger.info("Live trading started for exchange: {}.", exchange);
    }


    public void setTradePairs(List<TradePair> tradePairs) {
        // Set trade pairs for live trade processing
        if (tradePairs == null || tradePairs.isEmpty()) {
            logger.warn("No trade pairs provided. Unable to set trade pairs for live trading.");
            return;
        }

        // Clear any existing trade pairs if necessary (depends on your logic)
        this.tradePairs.clear();

        // Implement trade pair selection and processing logic
        for (TradePair pair : tradePairs) {
            if (pair != null) {
                // Perform validation on the TradePair, e.g., check if base and counter-currencies are valid
                if (isValidTradePair(pair)) {
                    // Add the valid trade pair to the list for further processing
                    this.tradePairs.add(pair);
                    logger.info("Added trade pair: {} to live trading.", pair);
                } else {
                    logger.error("Invalid trade pair: {}", pair);
                }
            }
        }

        // Further processing, e.g., start listening to live market data for these pairs
        if (!this.tradePairs.isEmpty()) {
            startLiveTrading(this.tradePairs);
            logger.info("Live trading started for {} pairs.", this.tradePairs.size());
        } else {
            logger.warn("No valid trade pairs to process for live trading.");
        }
    }

    // Helper method to validate a TradePair
    private boolean isValidTradePair(@NotNull TradePair pair) {
        return pair.getBaseCurrency() != null && pair.getCounterCurrency() != null;
    }

    // Example method to start live trading for selected pairs
    private void startLiveTrading(@NotNull List<TradePair> tradePairs) {
        // Implement logic to start live trading or listen to live market data for these pairs
        for (TradePair pair : tradePairs) {
            logger.debug("Starting live trade processing for pair: {}", pair);
            // Subscribe to market data feed, execute trades, etc.
        }
    }

    public void setAccount(Account account) {
        // Set account for live trade processing
        if (account == null) {
            logger.warn("No account provided. Unable to set account for live trading.");
            return;
        }


        logger.info("Added account: {} to live trading.", account);

        // Further processing, e.g., starts listening to live market data for this account
        startLiveTrading(this.tradePairs);
        logger.info("Live trading started for account: {}.", account);
    }

    public void setCandlePageConsumer(Consumer<List<CandleData>> candlePageConsumer) {
        // Set candle page consumer for live trade processing

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
