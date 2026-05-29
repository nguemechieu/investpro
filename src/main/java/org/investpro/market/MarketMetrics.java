package org.investpro.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.*;

/**
 * Professional market metrics and analysis for trading pairs.
 * Provides advanced market statistics including volatility, momentum, and
 * technical signals.
 */
@Slf4j
@Data
@Builder

public class MarketMetrics {

    private String symbol;
    private double currentPrice;
    private double bid;
    private double ask;
    private double spread;
    private double spreadPercent;
    private double volume24h;
    private double high24h;
    private double low24h;
    private double changePercent24h;
    private double volatility; // Daily volatility %
    private double priceChangeFromHigh; // % below 24h high
    private double priceChangeFromLow; // % above 24h low
    private double highLowRange; // 24h high-low range
    private double highLowRangePercent; // 24h high-low range %
    private String technicalSignal; // STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
    private double technicalScore; // -100 to +100
    private String volatilityLevel; // LOW, NORMAL, HIGH, EXTREME
    private String trend; // STRONG_UP, UP, SIDEWAYS, DOWN, STRONG_DOWN
    private double trendStrength; // 0-100
    private Instant calculatedAt;

    /**
     * Calculate metrics from a TradePair and price history
     */
    public static MarketMetrics from(TradePair pair, List<Double> priceHistory) {
        if (pair == null) {
            return null;
        }

        double currentPrice = pair.getLast() > 0 ? pair.getLast() : (pair.getBid() + pair.getAsk()) / 2.0;
        double bid = pair.getBid();
        double ask = pair.getAsk();
        double spread = ask - bid;
        double spreadPercent = bid > 0 ? (spread / bid) * 100.0 : 0.0;
        double high24h = pair.getHigh24h();
        double low24h = pair.getLow24h();
        double volume24h = pair.getVolume();
        double changePercent24h = pair.getChangePercent();

        // Calculate derived metrics
        double priceChangeFromHigh = high24h > 0 ? ((high24h - currentPrice) / high24h) * 100.0 : 0.0;
        double priceChangeFromLow = low24h > 0 ? ((currentPrice - low24h) / low24h) * 100.0 : 0.0;
        double highLowRange = high24h - low24h;
        double highLowRangePercent = low24h > 0 ? (highLowRange / low24h) * 100.0 : 0.0;

        // Calculate volatility
        double volatility = calculateVolatility(priceHistory, currentPrice);
        String volatilityLevel = assessVolatilityLevel(volatility);

        // Calculate trend
        String trend = calculateTrend(priceHistory, changePercent24h);
        double trendStrength = calculateTrendStrength(priceHistory);

        // Calculate technical score and signal
        TechnicalAnalysis technicalAnalysis = calculateTechnicalScore(
                currentPrice, high24h, low24h, changePercent24h, volatility, trendStrength);

        return MarketMetrics.builder()
                .symbol(pair.toString('/'))
                .currentPrice(currentPrice)
                .bid(bid)
                .ask(ask)
                .spread(spread)
                .spreadPercent(Math.round(spreadPercent * 10000.0) / 10000.0)
                .volume24h(volume24h)
                .high24h(high24h)
                .low24h(low24h)
                .changePercent24h(changePercent24h)
                .volatility(Math.round(volatility * 100.0) / 100.0)
                .priceChangeFromHigh(Math.round(priceChangeFromHigh * 100.0) / 100.0)
                .priceChangeFromLow(Math.round(priceChangeFromLow * 100.0) / 100.0)
                .highLowRange(highLowRange)
                .highLowRangePercent(Math.round(highLowRangePercent * 100.0) / 100.0)
                .technicalSignal(technicalAnalysis.signal)
                .technicalScore(technicalAnalysis.score)
                .volatilityLevel(volatilityLevel)
                .trend(trend)
                .trendStrength(Math.round(trendStrength * 100.0) / 100.0)
                .calculatedAt(Instant.now())
                .build();
    }

    /**
     * Calculate volatility as standard deviation of returns
     */
    private static double calculateVolatility(List<Double> priceHistory, double currentPrice) {
        if (priceHistory == null || priceHistory.size() < 2 || currentPrice <= 0) {
            return 0.0;
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < priceHistory.size(); i++) {
            double prev = priceHistory.get(i - 1);
            double curr = priceHistory.get(i);
            if (prev > 0) {
                returns.add(((curr - prev) / prev) * 100.0);
            }
        }

        if (returns.isEmpty()) {
            return 0.0;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    /**
     * Assess volatility level based on percentage
     */
    private static String assessVolatilityLevel(double volatility) {
        if (volatility < 1.0)
            return "LOW";
        if (volatility < 3.0)
            return "NORMAL";
        if (volatility < 7.0)
            return "HIGH";
        return "EXTREME";
    }

    /**
     * Calculate trend direction
     */
    private static String calculateTrend(List<Double> priceHistory, double changePercent24h) {
        if (priceHistory == null || priceHistory.size() < 5) {
            if (changePercent24h > 2.0)
                return "STRONG_UP";
            if (changePercent24h > 0.5)
                return "UP";
            if (changePercent24h < -2.0)
                return "STRONG_DOWN";
            if (changePercent24h < -0.5)
                return "DOWN";
            return "SIDEWAYS";
        }

        double avgRecentPrice = priceHistory.stream()
                .skip(Math.max(0, priceHistory.size() - 5))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double avgOlderPrice = priceHistory.stream()
                .limit(Math.min(5, priceHistory.size() - 1))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(avgRecentPrice);

        if (avgOlderPrice <= 0)
            return "SIDEWAYS";

        double trendPercent = ((avgRecentPrice - avgOlderPrice) / avgOlderPrice) * 100.0;

        if (trendPercent > 5.0)
            return "STRONG_UP";
        if (trendPercent > 1.0)
            return "UP";
        if (trendPercent < -5.0)
            return "STRONG_DOWN";
        if (trendPercent < -1.0)
            return "DOWN";
        return "SIDEWAYS";
    }

    /**
     * Calculate trend strength 0-100
     */
    private static double calculateTrendStrength(List<Double> priceHistory) {
        if (priceHistory == null || priceHistory.size() < 2) {
            return 0.0;
        }

        double firstPrice = priceHistory.get(0);
        double lastPrice = priceHistory.get(priceHistory.size() - 1);
        if (firstPrice <= 0) {
            return 0.0;
        }

        double overallChange = Math.abs(((lastPrice - firstPrice) / firstPrice) * 100.0);
        return Math.min(100.0, overallChange);
    }

    /**
     * Calculate technical score and signal
     */
    private static TechnicalAnalysis calculateTechnicalScore(double currentPrice, double high24h,
            double low24h, double changePercent, double volatility, double trendStrength) {

        int score = 0;

        // Price position in range (RSI-like)
        if (high24h > low24h && high24h > 0) {
            double rsiLike = ((currentPrice - low24h) / (high24h - low24h)) * 100.0;
            if (rsiLike > 70)
                score -= 20; // Overbought
            else if (rsiLike > 60)
                score -= 10;
            else if (rsiLike < 30)
                score += 20; // Oversold
            else if (rsiLike < 40)
                score += 10;
        }

        // Momentum
        if (changePercent > 5.0)
            score += 15;
        else if (changePercent > 2.0)
            score += 10;
        else if (changePercent < -5.0)
            score -= 15;
        else if (changePercent < -2.0)
            score -= 10;

        // Trend strength
        score += (int) (trendStrength / 2.5);

        // Volatility factor
        if (volatility > 5.0)
            score -= 5; // High volatility reduces confidence

        score = Math.max(-100, Math.min(100, score));

        String signal;
        if (score >= 60)
            signal = "STRONG_BUY";
        else if (score >= 20)
            signal = "BUY";
        else if (score <= -60)
            signal = "STRONG_SELL";
        else if (score <= -20)
            signal = "SELL";
        else
            signal = "NEUTRAL";

        return new TechnicalAnalysis(signal, score);
    }

    /**
     * Get color code for technical signal
     */
    public String getSignalColor() {
        return switch (technicalSignal) {
            case "STRONG_BUY" -> "#10b981"; // Green
            case "BUY" -> "#34d399"; // Light green
            case "NEUTRAL" -> "#6b7280"; // Gray
            case "SELL" -> "#fbbf24"; // Orange
            case "STRONG_SELL" -> "#ef4444"; // Red
            default -> "#6b7280";
        };
    }

    /**
     * Get color for price change
     */
    public String getPriceChangeColor() {
        if (changePercent24h > 0)
            return "#10b981"; // Green
        if (changePercent24h < 0)
            return "#ef4444"; // Red
        return "#6b7280"; // Gray
    }

    /**
     * Format volume for display
     */
    public String getFormattedVolume() {
        if (volume24h >= 1_000_000_000) {
            return String.format("%.2fB", volume24h / 1_000_000_000);
        } else if (volume24h >= 1_000_000) {
            return String.format("%.2fM", volume24h / 1_000_000);
        } else if (volume24h >= 1_000) {
            return String.format("%.2fK", volume24h / 1_000);
        } else {
            return String.format("%.2f", volume24h);
        }
    }

    @Data
    @AllArgsConstructor
    private static class TechnicalAnalysis {
        String signal;
        int score;
    }
}
