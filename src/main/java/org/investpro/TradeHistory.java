package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class TradeHistory {

    // Thread-safe list for storing trade history
    private static final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();

    // Thread-safe map for storing trade signals
    private final ConcurrentHashMap<TradePair, SIGNAL> signalMap = new ConcurrentHashMap<>();

    // Constructor (optional, but kept for extensibility)
    public TradeHistory() throws Exception {

        // Initialize trade history with some sample data (optional)
        // Example:


        addTrade(new Trade(TradePair.of("BTC", "USD"), 0.5, 0.01, Side.BUY, 1234567890L, Instant.now()));
        addTrade(new Trade(TradePair.of("ETH", "USD"), 0.04, 0.05, Side.SELL, 9876543210L, Instant.now().plusSeconds(3600)));
    }

    /**
     * Add a trade to the history.
     *
     * @param trade The trade to be added.
     */
    public static void addTrade(@NotNull Trade trade) {
        tradeHistory.add(trade);
    }

    /**
     * Retrieve all trades in the history.
     * @return A new list containing all trades (preserves encapsulation).
     */
    public List<Trade> getAllTrades() {
        return List.copyOf(tradeHistory); // Immutable copy for thread safety
    }

    /**
     * Retrieve trades for a specific TradePair.
     *
     * @param tradePair The trade pair to filter trades.
     * @return A list of trades matching the given trade pair.
     */
    public List<Trade> getTradesByPair(@NotNull TradePair tradePair) {
        return tradeHistory.stream()
                .filter(trade -> trade.getTradePair().equals(tradePair))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the most recent N trades efficiently.
     * @param count The number of most recent trades to retrieve.
     * @return A list containing the most recent trades.
     */
    public List<Trade> getRecentTrades(int count) {
        int size = tradeHistory.size();
        if (size == 0) return List.of(); // Return empty list if no trades

        return tradeHistory.subList(Math.max(0, size - count), size); // Efficient recent trades retrieval
    }

    /**
     * Clear all trade history.
     */
    public void clearHistory() {
        tradeHistory.clear();
    }

    /**
     * Get the total number of trades stored.
     * @return The trade history size.
     */
    public int getTradeHistorySize() {
        return tradeHistory.size();
    }

    /**
     * Store a signal for a trade pair.
     *
     * @param tradePair The trade pair.
     * @param signal    The signal to store.
     */
    public void putSignal(@NotNull TradePair tradePair, @NotNull SIGNAL signal) {
        signalMap.put(tradePair, signal);
    }

    /**
     * Retrieve a signal for a trade pair.
     *
     * @param tradePair The trade pair.
     * @return The signal associated with the trade pair or null if not found.
     */
    public SIGNAL getSignal(@NotNull TradePair tradePair) {
        return signalMap.get(tradePair);
    }

    /**
     * Check if a trade pair has an associated signal.
     *
     * @param tradePair The trade pair.
     * @return True if a signal is stored, otherwise false.
     */
    public boolean hasSignal(@NotNull TradePair tradePair) {
        return signalMap.containsKey(tradePair);
    }

    /**
     * Remove a signal for a trade pair.
     *
     * @param tradePair The trade pair to remove.
     */
    public void removeSignal(@NotNull TradePair tradePair) {
        signalMap.remove(tradePair);
    }

    public double getEndTime() {
        if (tradeHistory.isEmpty()) return 0;
        return tradeHistory.getLast().getTimestamp().toEpochMilli();
    }
}
