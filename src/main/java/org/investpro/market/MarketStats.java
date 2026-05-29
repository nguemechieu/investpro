package org.investpro.market;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive market statistics for an asset.
 * Includes market cap, volume, all-time high, performance metrics, and
 * description.
 */
@Slf4j
@Data
@Builder
public class MarketStats {

    // Price & Market Cap Stats
    private double currentPrice;
    private double marketCap;
    private double marketCapRank; // e.g., #1
    private double volume24h;
    private double volumeChange24h; // percentage
    private double circulating;
    private String circulatingUnit; // BTC, ETH, etc
    private double fullyDilutedMarketCap;
    private double allTimeHigh;
    private String allTimeHighDate; // e.g., "Oct 2025"
    private double percentDownFromATH;

    // Performance Metrics
    private double performanceOneYear; // percentage change
    private double performanceOneMonth;
    private double performanceOneWeek;
    private double performanceOneDay;

    // Comparison Benchmarks
    private double vsEthOneYear;
    private double vsMarketOneYear;
    private double vsEthOneMonth;
    private double vsMarketOneMonth;

    // Metadata
    private String coinbasePopularityRank;
    private String symbol;
    private String name;
    private String description;
    private String websiteUrl;
    private String whitePaperUrl;
    private String githubUrl;
    private String twitterUrl;

    /**
     * Format market cap for display
     */
    public String getFormattedMarketCap() {
        if (marketCap >= 1_000_000_000_000L) {
            return String.format("$%.1fT", marketCap / 1_000_000_000_000L);
        } else if (marketCap >= 1_000_000_000) {
            return String.format("$%.1fB", marketCap / 1_000_000_000);
        } else if (marketCap >= 1_000_000) {
            return String.format("$%.1fM", marketCap / 1_000_000);
        }
        return String.format("$%.2f", marketCap);
    }

    /**
     * Format volume for display
     */
    public String getFormattedVolume24h() {
        if (volume24h >= 1_000_000_000_000L) {
            return String.format("$%.2fT", volume24h / 1_000_000_000_000L);
        } else if (volume24h >= 1_000_000_000) {
            return String.format("$%.2fB", volume24h / 1_000_000_000);
        } else if (volume24h >= 1_000_000) {
            return String.format("$%.2fM", volume24h / 1_000_000);
        }
        return String.format("$%.2f", volume24h);
    }

    /**
     * Format fully diluted market cap
     */
    public String getFormattedFDMC() {
        if (fullyDilutedMarketCap >= 1_000_000_000_000L) {
            return String.format("$%.1fT", fullyDilutedMarketCap / 1_000_000_000_000L);
        } else if (fullyDilutedMarketCap >= 1_000_000_000) {
            return String.format("$%.1fB", fullyDilutedMarketCap / 1_000_000_000);
        } else if (fullyDilutedMarketCap >= 1_000_000) {
            return String.format("$%.1fM", fullyDilutedMarketCap / 1_000_000);
        }
        return String.format("$%.2f", fullyDilutedMarketCap);
    }

    /**
     * Get color for performance metric
     */
    public String getPerformanceColor(double value) {
        if (value > 0)
            return "#10b981"; // Green
        if (value < 0)
            return "#ef4444"; // Red
        return "#6b7280"; // Gray
    }

    /**
     * Get color for price drop from ATH
     */
    public String getATHColor() {
        if (percentDownFromATH < 10)
            return "#10b981"; // Near ATH
        if (percentDownFromATH < 30)
            return "#f59e0b"; // Moderate
        if (percentDownFromATH < 50)
            return "#ef7c42"; // Significant
        return "#ef4444"; // Large drop
    }

    /**
     * Create dummy/sample stats for UI testing
     */
    public static MarketStats createDummyBitcoinStats() {
        return MarketStats.builder()
                .currentPrice(100000)
                .marketCap(1600000000000.0)
                .marketCapRank(1)
                .volume24h(41200000000.0)
                .volumeChange24h(2.57)
                .circulating(20000000)
                .circulatingUnit("BTC")
                .fullyDilutedMarketCap(1700000000000.0)
                .allTimeHigh(126200)
                .allTimeHighDate("Oct 2025")
                .percentDownFromATH(35.71)
                .performanceOneYear(-16.35)
                .performanceOneMonth(5.20)
                .performanceOneWeek(8.10)
                .performanceOneDay(1.30)
                .vsEthOneYear(-34.92)
                .vsMarketOneYear(-12.50)
                .vsEthOneMonth(3.20)
                .vsMarketOneMonth(2.10)
                .coinbasePopularityRank("#1")
                .symbol("BTC")
                .name("Bitcoin")
                .description(
                        "The world's first cryptocurrency, Bitcoin is stored and exchanged securely on the internet through a digital ledger known as a blockchain. Bitcoins are divisible into smaller units known as satoshis — each satoshi is worth 0.00000001 bitcoin.")
                .websiteUrl("https://bitcoin.org")
                .whitePaperUrl("https://bitcoin.org/bitcoin.pdf")
                .githubUrl("https://github.com/bitcoin")
                .twitterUrl("https://twitter.com/bitcoin")
                .build();
    }
}
