package org.investpro.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for Behaviour Guard - trading behavior protection and risk
 * limits.
 * <p>
 * Encapsulates all guard settings including:
 * - Drawdown and equity protection
 * - Win/loss streak limits
 * - Trading hours restrictions
 * - Volatility filtering
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class BehaviourGuardConfig {

    /**
     * Master switch for behaviour guard
     */
    private Boolean guardEnabled;

    /**
     * Enable drawdown protection
     */
    private Boolean drawdownProtectionEnabled;

    /**
     * Maximum allowed drawdown percentage (0-100)
     */
    private Double maxDrawdownPercent;

    /**
     * Enable equity guard
     */
    private Boolean equityGuardEnabled;

    /**
     * Minimum equity threshold in account currency
     */
    private Double minEquityThreshold;

    /**
     * Enable win streak limiting
     */
    private Boolean winStreakLimitEnabled;

    /**
     * Maximum consecutive winning trades before pause
     */
    private Integer maxConsecutiveWins;

    /**
     * Enable loss streak limiting
     */
    private Boolean lossStreakLimitEnabled;

    /**
     * Maximum consecutive losing trades before pause
     */
    private Integer maxConsecutiveLosses;

    /**
     * Enable trading hours restriction
     */
    private Boolean tradingHoursEnabled;

    /**
     * Trading start time in UTC (HH:MM format)
     */
    private String tradingStartTime;

    /**
     * Trading end time in UTC (HH:MM format)
     */
    private String tradingEndTime;

    /**
     * Enable volatility filter
     */
    private Boolean volatilityFilterEnabled;

    /**
     * Maximum volatility threshold percentage (0-100)
     */
    private Double maxVolatilityPercent;

    /**
     * Volatility source for filtering (VIX, ATR, Historical, Implied)
     */
    private String volatilitySource;

    /**
     * Custom notes and configuration documentation
     */
    private String notes;

    /**
     * Default configuration with conservative settings
     */
    public static BehaviourGuardConfig defaults() {
        return BehaviourGuardConfig.builder()
                .guardEnabled(true)
                .drawdownProtectionEnabled(true)
                .maxDrawdownPercent(5.0)
                .equityGuardEnabled(true)
                .minEquityThreshold(1000.0)
                .winStreakLimitEnabled(true)
                .maxConsecutiveWins(10)
                .lossStreakLimitEnabled(true)
                .maxConsecutiveLosses(5)
                .tradingHoursEnabled(false)
                .tradingStartTime("00:00")
                .tradingEndTime("23:59")
                .volatilityFilterEnabled(true)
                .maxVolatilityPercent(50.0)
                .volatilitySource("ATR")
                .notes("")
                .build();
    }

    /**
     * Check if guard is fully operational
     */
    public boolean isOperational() {
        return guardEnabled != null && guardEnabled;
    }

    /**
     * Check if in allowed trading hours
     */
    public boolean isInTradingHours() {
        if (!tradingHoursEnabled) {
            return true;
        }

        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("UTC"));
        java.time.LocalTime start = java.time.LocalTime.parse(tradingStartTime);
        java.time.LocalTime end = java.time.LocalTime.parse(tradingEndTime);

        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            // Crosses midnight
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "BehaviourGuardConfig{enabled=%s, maxDD=%.1f%%, minEquity=$%.2f, maxWins=%d, maxLosses=%d, vol=%.1f%%, hours=%s-%s}",
                guardEnabled, maxDrawdownPercent, minEquityThreshold, maxConsecutiveWins, maxConsecutiveLosses,
                maxVolatilityPercent, tradingStartTime, tradingEndTime);
    }
}
