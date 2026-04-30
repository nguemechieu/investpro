package org.investpro.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing and processing market data from exchanges.
 * Provides helper methods for converting raw JSON responses into domain models.
 */
@Data
@Getter
@Setter
public class MarketDataParser {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static List<Position> positions;

    /**
     * Parse a generic order book JSON response
     * Format: {"bids": [[price, size], ...], "asks": [[price, size], ...]}
     */
    public static @NotNull OrderBook parseGenericOrderBook(String jsonResponse, TradePair tradePair) {
        OrderBook orderBook = new OrderBook(tradePair);
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Parse bids
            JsonNode bidsNode = root.get("bids");
            if (bidsNode != null && bidsNode.isArray()) {
                for (JsonNode bid : bidsNode) {
                    if (bid.size() >= 2) {
                        double price = bid.get(0).asDouble();
                        double size = bid.get(1).asDouble();
                        orderBook.getBids().add(new OrderBook.PriceLevel(price, size));
                    }
                }
            }

            // Parse asks
            JsonNode asksNode = root.get("asks");
            if (asksNode != null && asksNode.isArray()) {
                for (JsonNode ask : asksNode) {
                    if (ask.size() >= 2) {
                        double price = ask.get(0).asDouble();
                        double size = ask.get(1).asDouble();
                        orderBook.getAsks().add(new OrderBook.PriceLevel(price, size));
                    }
                }
            }

            orderBook.setTimestamp(Instant.now());
        } catch (Exception e) {
            logger.error("Failed to parse generic order book", e);
        }
        return orderBook;
    }

    /**
     * Calculate buy/sell imbalance ratio
     * Returns ratio of bid volume to total volume
     */
    public static double calculateBuySellImbalance(OrderBook orderBook) {
        double bidVolume = orderBook.getTotalBidVolume();
        double askVolume = orderBook.getTotalAskVolume();
        double totalVolume = bidVolume + askVolume;

        if (totalVolume == 0) {
            return 0.5; // neutral
        }

        return bidVolume / totalVolume;
    }

    /**
     * Get weighted average price for bids
     */
    public static double getWeightedAverageBidPrice(OrderBook orderBook) {
        double totalPrice = 0;
        double totalSize = 0;

        for (OrderBook.PriceLevel bid : orderBook.getBids()) {
            totalPrice += bid.getPrice() * bid.getSize();
            totalSize += bid.getSize();
        }

        return totalSize > 0 ? totalPrice / totalSize : 0;
    }

    /**
     * Get weighted average price for asks
     */
    public static double getWeightedAverageAskPrice(OrderBook orderBook) {
        double totalPrice = 0;
        double totalSize = 0;

        for (OrderBook.PriceLevel ask : orderBook.getAsks()) {
            totalPrice += ask.getPrice() * ask.getSize();
            totalSize += ask.getSize();
        }

        return totalSize > 0 ? totalPrice / totalSize : 0;
    }

    /**
     * Get cumulative volume at or better than a given price level for bids
     */
    public static double getCumulativeBidVolume(OrderBook orderBook, double minPrice) {
        return orderBook.getBids().stream()
                .filter(level -> level.getPrice() >= minPrice)
                .mapToDouble(OrderBook.PriceLevel::getSize)
                .sum();
    }

    /**
     * Get cumulative volume at or better than a given price level for asks
     */
    public static double getCumulativeAskVolume(OrderBook orderBook, double maxPrice) {
        return orderBook.getAsks().stream()
                .filter(level -> level.getPrice() <= maxPrice)
                .mapToDouble(OrderBook.PriceLevel::getSize)
                .sum();
    }

    /**
     * Detect potential market micro-structure signals
     * Returns: "BULLISH" if bid volume >> ask volume
     *          "BEARISH" if ask volume >> bid volume
     *          "NEUTRAL" otherwise
     */
    public static String detectOrderBookSignal(OrderBook orderBook) {
        double imbalance = calculateBuySellImbalance(orderBook);

        if (imbalance > 0.65) {
            return "BULLISH";
        } else if (imbalance < 0.35) {
            return "BEARISH";
        } else {
            return "NEUTRAL";
        }
    }

    /**
     * Get average position size from list of positions
     */
    public static double getAveragePositionSize(List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return 0;
        }

        return positions.stream()
                .mapToDouble(Position::getQuantity)
                .average()
                .orElse(0);
    }

    /**
     * Get total P&L from positions
     */
    public static double getTotalPositionPnL(List<Position> positions) {
        MarketDataParser.positions = positions;
        if (positions == null || positions.isEmpty()) {
            return 0;
        }

        return positions.stream()
                .mapToDouble(Position::getUnrealizedPnl)
                .sum();
    }

    /**
     * Get average entry price across positions
     */
    public static double getAverageEntryPrice(List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return 0;
        }

        double totalValue = 0;
        double totalQuantity = 0;

        for (Position pos : positions) {

                if(pos.getQuantity() > 0) {
                    totalValue += pos.getEntryPrice() * pos.getQuantity();
                    totalQuantity += pos.getQuantity();
                }
        }

        return totalQuantity > 0 ? totalValue / totalQuantity : 0;
    }

    /**
     * Filter positions by side (LONG/SHORT)
     */
    public static List<Position> filterPositionsBySide(List<Position> positions, Side side) {
        List<Position> filtered = new ArrayList<>();
        if (positions != null) {
            for (Position pos : positions) {
                if (pos.getSide() == side) {
                    filtered.add(pos);
                }
            }
        }
        return filtered;
    }

    /**
     * Get total traded volume from trades list
     */
    public static double getTotalTradeVolume(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0;
        }

        return trades.stream()
                .mapToDouble(t -> t.getAmount())
                .sum();
    }

    /**
     * Calculate volume-weighted average price (VWAP) from trades
     */
    public static double calculateVWAP(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0;
        }

        double totalValue = 0;
        double totalVolume = 0;

        for (Trade trade : trades) {
            double price = trade.getPrice();
            double amount = trade.getAmount();
            totalValue += price * amount;
            totalVolume += amount;
        }

        return totalVolume > 0 ? totalValue / totalVolume : 0;
    }

    public static List<Position> getPositions() {
        return positions;
    }

    public static void setPositions(List<Position> positions) {
        MarketDataParser.positions = positions;
    }
}
