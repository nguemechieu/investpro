package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TradeHistory {
    static List<Trade> tradeHistory = new ArrayList<>();

    // Constructor initializes the trade history
    public TradeHistory() {
    }

    public static void addTradeToHistory(@NotNull Trade trade) {
        // Add trade to the history
        TradeHistory.addTrade(trade);
        // Alternatively, you could use a database to store the trade history
    }

    // Add a trade to the history
    public static void addTrade(Trade trade) {

        TradeHistory.tradeHistory.add(trade); // Add trade to the history

    }

    // Retrieve all trades in the history
    public List<Trade> getAllTrades() {
        return new ArrayList<>(tradeHistory); // Return a copy to preserve encapsulation
    }

    // Retrieve trades for a specific TradePair
    public List<Trade> getTradesByPair(TradePair tradePair) {
        return tradeHistory.stream()
                .filter(trade -> trade.getTradePair().equals(tradePair))
                .collect(Collectors.toList());
    }

    // Retrieve the most recent N trades
    public List<Trade> getRecentTrades(int count) {
        return tradeHistory.stream()
                .skip(Math.max(0, tradeHistory.size() - count)) // Skip older trades if there are more than "count"
                .collect(Collectors.toList());
    }

    // Clear all trade history
    public void clearHistory() {
        tradeHistory.clear();
    }

    // Get the size of the trade history
    public int getTradeHistorySize() {
        return tradeHistory.size();
    }
}
