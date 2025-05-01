package org.investpro.investpro;

import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TradeStorage {

    private static final Logger logger = LoggerFactory.getLogger(TradeStorage.class);

    // List to hold trades in memory
    private static List<Trade> trades = new ArrayList<>();

    // Constructor initializes the trade list
    public TradeStorage() {
        trades = new ArrayList<>();
        logger.debug("TradeStorage initialized.");
    }

    // Method to add a trade to the storage
    public static void addTrade(Trade trade) {
        trades.add(trade);
        logger.debug("Trade added: {}", trade);
    }

    // Method to retrieve all stored trades
    public List<Trade> getAllTrades() {
        return new ArrayList<>(trades);  // Return a copy to avoid external modification
    }

    // Method to get trades by trade pair
    public List<Trade> getTradesByPair(TradePair tradePair) {
        return trades.stream()
                .filter(trade -> trade.getTradePair().equals(tradePair))
                .collect(Collectors.toList());
    }

    // Method to clear all stored trades
    public void clearTrades() {
        trades.clear();
        logger.debug("All trades cleared from storage.");
    }

    // Method to get the total number of stored trades
    public int getTradeCount() {
        return trades.size();
    }

    // Method to remove a specific trade from storage
    public boolean removeTrade(Trade trade) {
        boolean removed = trades.remove(trade);
        if (removed) {
            logger.debug("Trade removed: {}", trade);
        } else {
            logger.debug("Trade not found: {}", trade);
        }
        return removed;
    }
}
