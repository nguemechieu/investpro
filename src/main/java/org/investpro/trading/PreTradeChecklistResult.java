package org.investpro.trading;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Result of the complete pre-trade validation checklist.
 * Contains all validation decisions, risk metrics, and audit trail information.
 *
 * <p>
 * This object is intentionally immutable and audit-friendly. It should be produced
 * by PreTradeValidationEngine before any order reaches OrderRouter / ExecutionManager.
 *
 * <p>
 * Decision rules:
 * <ul>
 *     <li>Any blocker = rejected</li>
 *     <li>Approved=false = rejected</li>
 *     <li>3 or more warnings = rejected</li>
 *     <li>1 warning = size reduced to 75%</li>
 *     <li>2 warnings = size reduced to 50%</li>
 * </ul>
 *
 * @author InvestPro Trading System
 */

public record PreTradeChecklistResult(
        // Decision & Identity
        boolean approved,
        @NotNull String decisionId,
        @NotNull Instant decisionTime,

        // Trade Parameters
        @NotNull TradePair tradePair,
        @NotNull Side side,
        double entryPrice,

        // Position & Risk
        double finalPositionSize,
        double stopLoss,
        double takeProfit,
        double riskAmount,
        double riskRewardRatio,

        // Confidence & Strategy
        double confidence,
         String strategyName,
        String confirmationStrategy,

        // Market Context
        double bid,
        double ask,
        double spread,
        double spreadPercent,
         String marketRegime,

        // Account State
        double accountEquity,
        double freeMargin,
        double marginUsagePercent,
        int openPositionCount,

        // Validation Status
        @NotNull List<String> passedChecks,
        @NotNull List<String> warnings,
        @NotNull List<String> blockers,

        // AI Decision
         String aiDecision,
         String aiReason,

        // Execution Plan
         String orderType,
         String timeInForce,

        // Audit
         String auditLog
) {

    public PreTradeChecklistResult {
        decisionId = requireText(decisionId, "decisionId");

        passedChecks = List.copyOf(passedChecks);
        warnings = List.copyOf(warnings);
        blockers = List.copyOf(blockers);

        finalPositionSize = sanitizeNonNegative(finalPositionSize);
        entryPrice = sanitizeNonNegative(entryPrice);
        stopLoss = sanitizeNonNegative(stopLoss);
        takeProfit = sanitizeNonNegative(takeProfit);
        riskAmount = sanitizeNonNegative(riskAmount);
        riskRewardRatio = sanitizeNonNegative(riskRewardRatio);

        confidence = clamp(confidence, 0.0, 1.0);

        bid = sanitizeNonNegative(bid);
        ask = sanitizeNonNegative(ask);
        spread = sanitizeNonNegative(spread);
        spreadPercent = sanitizeNonNegative(spreadPercent);

        accountEquity = sanitizeNonNegative(accountEquity);
        freeMargin = sanitizeNonNegative(freeMargin);
        marginUsagePercent = sanitizeNonNegative(marginUsagePercent);
        openPositionCount = Math.max(0, openPositionCount);
    }

    /**
     * Determine if the trade should be rejected.
     *
     * <p>
     * Rejected when:
     * <ul>
     *     <li>approved is false</li>
     *     <li>there is at least one blocker</li>
     *     <li>there are 3 or more warnings</li>
     * </ul>
     */
    public boolean rejected() {
        return !approved || !blockers.isEmpty() || shouldRejectDueToWarnings();
    }

    /**
     * Determine if this decision may continue to execution.
     */
    public boolean canExecute() {
        return !rejected() && getAdjustedPositionSize() > 0.0;
    }

    /**
     * Calculate size reduction based on warning count.
     *
     * <ul>
     *     <li>No warnings: 100%</li>
     *     <li>1 warning: 75%</li>
     *     <li>2 warnings: 50%</li>
     *     <li>3+ warnings: 0%, reject</li>
     * </ul>
     */
    public double getAdjustedSizeMultiplier() {
        return switch (warnings.size()) {
            case 0 -> 1.0;
            case 1 -> 0.75;
            case 2 -> 0.50;
            default -> 0.0;
        };
    }

    /**
     * Get adjusted position size based on warning count.
     */
    public double getAdjustedPositionSize() {
        if (rejected()) {
            return 0.0;
        }

        return finalPositionSize * getAdjustedSizeMultiplier();
    }

    /**
     * Check if trade should be rejected due to warning count.
     */
    public boolean shouldRejectDueToWarnings() {
        return warnings.size() >= 3;
    }

    /**
     * Returns the account risk percentage.
     */
    public double riskPercentOfEquity() {
        if (accountEquity <= 0.0 || riskAmount <= 0.0) {
            return 0.0;
        }

        return riskAmount / accountEquity;
    }

    /**
     * Returns true if this result contains warnings but no blockers.
     */
    public boolean approvedWithWarnings() {
        return approved && blockers.isEmpty() && !warnings.isEmpty() && !shouldRejectDueToWarnings();
    }

    /**
     * Get human-readable summary of decision.
     */
    @NotNull
    public String getSummary() {
        if (rejected()) {
            String reason;

            if (!blockers.isEmpty()) {
                reason = blockers.get(0);
            } else if (shouldRejectDueToWarnings()) {
                reason = "Too many warnings (" + warnings.size() + ")";
            } else {
                reason = "Trade was not approved";
            }

            return "REJECTED: " + reason;
        }

        String sizeAdjustment = "";
        if (!warnings.isEmpty()) {
            double multiplier = getAdjustedSizeMultiplier();
            sizeAdjustment = String.format(
                    Locale.US,
                    " (size reduced to %.0f%% due to %d warning%s)",
                    multiplier * 100,
                    warnings.size(),
                    warnings.size() == 1 ? "" : "s"
            );
        }

        return String.format(
                Locale.US,
                "APPROVED: %s %s @ %s, size %s%s, R:R %.2f, confidence %.1f%%",
                side,
                tradePair,
                formatNumber(entryPrice),
                formatNumber(getAdjustedPositionSize()),
                sizeAdjustment,
                riskRewardRatio,
                confidence * 100.0
        );
    }

    /**
     * Get detailed audit message with all decision parameters.
     */
    @NotNull
    public String getAuditMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== PRE-TRADE VALIDATION DECISION ===\n");
        sb.append(String.format(Locale.US, "Decision ID: %s%n", decisionId));
        sb.append(String.format(Locale.US, "Time: %s%n", decisionTime));
        sb.append(String.format(Locale.US, "Status: %s%n", rejected() ? "REJECTED" : "APPROVED"));
        sb.append(String.format(Locale.US, "Executable: %s%n", canExecute() ? "YES" : "NO"));

        sb.append(String.format(
                Locale.US,
                "Symbol: %s %s @ %s%n",
                side,
                tradePair,
                formatNumber(entryPrice)
        ));

        sb.append(String.format(
                Locale.US,
                "Position Size: %s adjusted to %s%n",
                formatNumber(finalPositionSize),
                formatNumber(getAdjustedPositionSize())
        ));

        sb.append(String.format(
                Locale.US,
                "Stop Loss: %s | Take Profit: %s | R:R: %.2f%n",
                formatNumber(stopLoss),
                formatNumber(takeProfit),
                riskRewardRatio
        ));

        sb.append(String.format(
                Locale.US,
                "Risk Amount: $%s (%.2f%% of equity)%n",
                formatNumber(riskAmount),
                riskPercentOfEquity() * 100.0
        ));

        sb.append(String.format(Locale.US, "Confidence: %.1f%%%n", confidence * 100.0));

        sb.append(String.format(
                Locale.US,
                "Strategy: %s",
                strategyName != null && !strategyName.isBlank() ? strategyName : "N/A"
        ));

        if (confirmationStrategy != null && !confirmationStrategy.isBlank()) {
            sb.append(String.format(Locale.US, " + %s", confirmationStrategy));
        }

        sb.append("\n");

        sb.append("\nMarket Context:\n");
        sb.append(String.format(
                Locale.US,
                "  Bid/Ask: %s / %s%n",
                formatNumber(bid),
                formatNumber(ask)
        ));
        sb.append(String.format(
                Locale.US,
                "  Spread: %s / %.4f%%%n",
                formatNumber(spread),
                spreadPercent * 100.0
        ));
        sb.append(String.format(
                Locale.US,
                "  Market Regime: %s%n",
                marketRegime != null && !marketRegime.isBlank() ? marketRegime : "Unknown"
        ));

        sb.append("\nAccount State:\n");
        sb.append(String.format(
                Locale.US,
                "  Equity: $%s | Free Margin: $%s | Usage: %.2f%%%n",
                formatNumber(accountEquity),
                formatNumber(freeMargin),
                marginUsagePercent
        ));
        sb.append(String.format(Locale.US, "  Open Positions: %d%n", openPositionCount));

        sb.append("\nValidation Results:\n");
        sb.append(String.format(Locale.US, "  Passed: %d checks%n", passedChecks.size()));

        if (!passedChecks.isEmpty()) {
            passedChecks.forEach(check -> sb.append(String.format(Locale.US, "    + %s%n", check)));
        }

        if (!warnings.isEmpty()) {
            sb.append(String.format(Locale.US, "  Warnings: %d%n", warnings.size()));
            warnings.forEach(warning -> sb.append(String.format(Locale.US, "    - %s%n", warning)));
        }

        if (!blockers.isEmpty()) {
            sb.append(String.format(Locale.US, "  Blockers: %d%n", blockers.size()));
            blockers.forEach(blocker -> sb.append(String.format(Locale.US, "    - %s%n", blocker)));
        }

        if (aiDecision != null && !aiDecision.isBlank()) {
            sb.append(String.format(Locale.US, "%nAI Review: %s%n", aiDecision));

            if (aiReason != null && !aiReason.isBlank()) {
                sb.append(String.format(Locale.US, "  Reason: %s%n", aiReason));
            }
        }

        sb.append(String.format(
                Locale.US,
                "%nExecution Plan: %s%n",
                orderType != null && !orderType.isBlank() ? orderType : "Not set"
        ));

        if (timeInForce != null && !timeInForce.isBlank()) {
            sb.append(String.format(Locale.US, "  Time in Force: %s%n", timeInForce));
        }

        if (auditLog != null && !auditLog.isBlank()) {
            sb.append("\nAdditional Audit Log:\n");
            sb.append(auditLog).append("\n");
        }

        sb.append("=====================================");

        return sb.toString();
    }

    /**
     * Create a rejected result quickly.
     */
    public static PreTradeChecklistResult rejected(
            @NotNull String decisionId,
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull String blocker
    ) {
        return new PreTradeChecklistResult(
                false,
                decisionId,
                Instant.now(),
                tradePair,
                side,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                null,
                null,
                0.0,
                0.0,
                0.0,
                0.0,
                null,
                0.0,
                0.0,
                0.0,
                0,
                List.of(),
                List.of(),
                List.of(blocker),
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Create a minimal approved result.
     */
    public static PreTradeChecklistResult approved(
            @NotNull String decisionId,
            @NotNull TradePair tradePair,
            @NotNull Side side,
            double entryPrice,
            double finalPositionSize,
            double confidence
    ) {
        return new PreTradeChecklistResult(
                true,
                decisionId,
                Instant.now(),
                tradePair,
                side,
                entryPrice,
                finalPositionSize,
                0.0,
                0.0,
                0.0,
                0.0,
                confidence,
                null,
                null,
                0.0,
                0.0,
                0.0,
                0.0,
                null,
                0.0,
                0.0,
                0.0,
                0,
                List.of("Minimal approval result created"),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }

        return value;
    }

    private static double sanitizeNonNegative(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, value);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }

    private static String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }

        if (Math.abs(value) >= 1000.0) {
            return String.format(Locale.US, "%,.2f", value);
        }

        if (Math.abs(value) >= 1.0) {
            return String.format(Locale.US, "%.4f", value);
        }

        if (Math.abs(value) >= 0.0001) {
            return String.format(Locale.US, "%.8f", value);
        }

        return String.format(Locale.US, "%.12f", value);
    }
}