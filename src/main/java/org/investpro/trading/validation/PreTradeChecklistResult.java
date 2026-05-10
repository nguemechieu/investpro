package org.investpro.trading.validation;

import org.investpro.utils.Side;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Result of the complete pre-trade validation checklist.
 * Contains all validation decisions, risk metrics, and audit trail information.
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
        @Nullable String strategyName,
        @Nullable String confirmationStrategy,

        // Market Context
        double bid,
        double ask,
        double spread,
        double spreadPercent,
        @Nullable String marketRegime,

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
        @Nullable String aiDecision,
        @Nullable String aiReason,

        // Execution Plan
        @Nullable String orderType,
        @Nullable String timeInForce,

        // Audit
        @Nullable String auditLog) {

    /**
     * Determine if the trade should be rejected.
     * Rejected if there are any blockers or it's not approved.
     */
    public boolean rejected() {
        return !approved || !blockers.isEmpty();
    }

    /**
     * Calculate size reduction based on warning count.
     * - No warnings: 100%
     * - 1 warning: 75%
     * - 2 warnings: 50%
     * - 3+ warnings: reject
     */
    public double getAdjustedSizeMultiplier() {
        return switch (warnings.size()) {
            case 0 -> 1.0;
            case 1 -> 0.75;
            case 2 -> 0.50;
            default -> 0.0; // reject
        };
    }

    /**
     * Get adjusted position size based on warning count.
     */
    public double getAdjustedPositionSize() {
        return finalPositionSize * getAdjustedSizeMultiplier();
    }

    /**
     * Check if trade should be rejected due to warnings (3+).
     */
    public boolean shouldRejectDueToWarnings() {
        return warnings.size() >= 3;
    }

    /**
     * Get human-readable summary of decision.
     */
    @NotNull
    public String getSummary() {
        if (rejected()) {
            String reason = blockers.isEmpty()
                    ? "Too many warnings (" + warnings.size() + ")"
                    : blockers.get(0);
            return String.format("REJECTED: %s", reason);
        }

        String sizeAdjustment = "";
        if (!warnings.isEmpty()) {
            double multiplier = getAdjustedSizeMultiplier();
            sizeAdjustment = String.format(" (size reduced to %.0f%% due to %d warning%s)",
                    multiplier * 100,
                    warnings.size(),
                    warnings.size() == 1 ? "" : "s");
        }

        return String.format("APPROVED: %s %s @ %.2f, size %.0f%s, R:R %.1f, confidence %.1f%%",
                side,
                tradePair,
                entryPrice,
                getAdjustedPositionSize(),
                sizeAdjustment,
                riskRewardRatio,
                confidence * 100);
    }

    /**
     * Get detailed audit message with all decision parameters.
     */
    @NotNull
    public String getAuditMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PRE-TRADE VALIDATION DECISION ===\n");
        sb.append(String.format("Decision ID: %s\n", decisionId));
        sb.append(String.format("Time: %s\n", decisionTime));
        sb.append(String.format("Status: %s\n", approved ? "APPROVED" : "REJECTED"));
        sb.append(String.format("Symbol: %s %s @ %.2f\n", side, tradePair, entryPrice));
        sb.append(
                String.format("Position Size: %.0f (adjusted: %.0f)\n", finalPositionSize, getAdjustedPositionSize()));
        sb.append(String.format("Stop Loss: %.2f | Take Profit: %.2f | R:R: %.1f\n", stopLoss, takeProfit,
                riskRewardRatio));
        sb.append(String.format("Risk Amount: $%.2f (%.2f%% of equity)\n", riskAmount,
                (riskAmount / accountEquity) * 100));
        sb.append(String.format("Confidence: %.1f%%\n", confidence * 100));
        sb.append(String.format("Strategy: %s", strategyName != null ? strategyName : "N/A"));
        if (confirmationStrategy != null) {
            sb.append(String.format(" + %s", confirmationStrategy));
        }
        sb.append("\n");

        sb.append(String.format("\nMarket Context:\n"));
        sb.append(String.format("  Bid/Ask: %.2f / %.2f (spread %.2f%%)\n", bid, ask, spreadPercent * 100));
        sb.append(String.format("  Market Regime: %s\n", marketRegime != null ? marketRegime : "Unknown"));

        sb.append(String.format("\nAccount State:\n"));
        sb.append(String.format("  Equity: $%.2f | Free Margin: $%.2f | Usage: %.1f%%\n",
                accountEquity, freeMargin, marginUsagePercent));
        sb.append(String.format("  Open Positions: %d\n", openPositionCount));

        sb.append(String.format("\nValidation Results:\n"));
        sb.append(String.format("  Passed: %d checks\n", passedChecks.size()));
        if (!warnings.isEmpty()) {
            sb.append(String.format("  Warnings: %d\n", warnings.size()));
            warnings.forEach(w -> sb.append(String.format("    - %s\n", w)));
        }
        if (!blockers.isEmpty()) {
            sb.append(String.format("  Blockers: %d\n", blockers.size()));
            blockers.forEach(b -> sb.append(String.format("    - %s\n", b)));
        }

        if (aiDecision != null) {
            sb.append(String.format("\nAI Review: %s\n", aiDecision));
            if (aiReason != null) {
                sb.append(String.format("  Reason: %s\n", aiReason));
            }
        }

        sb.append(String.format("\nExecution Plan: %s\n", orderType != null ? orderType : "Not set"));
        if (timeInForce != null) {
            sb.append(String.format("  Time in Force: %s\n", timeInForce));
        }

        sb.append("=====================================");
        return sb.toString();
    }
}
