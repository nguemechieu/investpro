package org.investpro.strategy.management;

import lombok.Builder;
import lombok.Getter;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.lifecycle.AIReviewDecision;
import org.investpro.strategy.lifecycle.StrategyHealthLevel;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyLifecycleStatus;
import org.investpro.strategy.lifecycle.StrategyValidationReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Institutional live-assignment safety gate for strategy lifecycle records.
 *
 * <p>User strategies remain advisory. This gatekeeper only decides whether a
 * strategy assignment may progress to platform-controlled live operation; it
 * never places orders and never bypasses RiskEngine, position sizing,
 * ExecutionPlanner, ExecutionRouter, or ExecutionEngine.</p>
 */
public final class StrategyAssignmentGatekeeper {

    private static volatile StrategyAssignmentGatekeeper instance;

    private final double minStrategyScore;
    private final double minBacktestScore;
    private final double minPaperTradingScore;
    private final double minRiskBehaviorScore;
    private final double maxPaperDrawdown;

    private StrategyAssignmentGatekeeper() {
        this.minStrategyScore = Double.parseDouble(System.getProperty("strategy.assignment.minStrategyScore", "60.0"));
        this.minBacktestScore = Double.parseDouble(System.getProperty("strategy.assignment.minBacktestScore", "60.0"));
        this.minPaperTradingScore = Double.parseDouble(System.getProperty("strategy.assignment.minPaperTradingScore", "60.0"));
        this.minRiskBehaviorScore = Double.parseDouble(System.getProperty("strategy.assignment.minRiskBehaviorScore", "0.60"));
        this.maxPaperDrawdown = Double.parseDouble(System.getProperty("strategy.assignment.maxPaperDrawdown", "0.25"));
    }

    /** @return singleton gatekeeper instance. */
    public static StrategyAssignmentGatekeeper getInstance() {
        StrategyAssignmentGatekeeper local = instance;
        if (local == null) {
            synchronized (StrategyAssignmentGatekeeper.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyAssignmentGatekeeper();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Checks whether a lifecycle record has enough evidence to be assigned live. */
    public GateResult canAssignLive(StrategyLifecycleRecord record) {
        return evaluate(record, false);
    }

    /** Checks whether a lifecycle record can be promoted to LIVE_ACTIVE. */
    public GateResult canPromoteLive(StrategyLifecycleRecord record) {
        return evaluate(record, true);
    }

    /** Checks whether the already-assigned strategy can process a signal for trading. */
    public GateResult canTrade(StrategyLifecycleRecord record) {
        List<String> failures = new ArrayList<>();
        if (record == null) {
            failures.add("Assignment record is missing");
            return GateResult.blocked(failures);
        }
        if (record.getLifecycleStatus() != StrategyLifecycleStatus.LIVE_ACTIVE
                && record.getLifecycleStatus() != StrategyLifecycleStatus.WATCH) {
            failures.add("Strategy is not live-active: status=" + record.getLifecycleStatus());
        }
        appendDegradationFailures(record, failures);
        if (!isMarketSessionActive(record.getSymbol())) {
            failures.add("Market session is not active for symbol " + record.getSymbol());
        }
        return failures.isEmpty() ? GateResult.allowed("Live strategy can trade") : GateResult.blocked(failures);
    }

    private GateResult evaluate(StrategyLifecycleRecord record, boolean promotion) {
        List<String> failures = new ArrayList<>();
        if (record == null) {
            failures.add("Assignment record is missing");
            return GateResult.blocked(failures);
        }

        StrategyLifecycleStatus status = record.getLifecycleStatus();
        if (status == null || !status.hasValidationEvidence()) {
            failures.add("VALIDATED/BACKTESTED/AI_REVIEWED evidence is incomplete: status=" + status);
        }

        if (promotion && !status.hasPaperApproval()) {
            failures.add("PAPER_APPROVED evidence is required before LIVE_ACTIVE");
        }

        if (record.getAssignmentScore() < minStrategyScore) {
            failures.add("Strategy score below minimum " + minStrategyScore);
        }

        double backtestScore = backtestScore(record);
        if (backtestScore < minBacktestScore) {
            failures.add("Backtest score below minimum " + minBacktestScore);
        }

        double paperScore = paperTradingScore(record);
        if (paperScore < minPaperTradingScore) {
            failures.add("Paper trading score below minimum " + minPaperTradingScore);
        }

        if (record.getAiApprovalStatus() != AIReviewDecision.APPROVE) {
            failures.add("AI review has not approved this strategy");
        }

        StrategyValidationReport validationReport = record.getLastValidationReport();
        if (validationReport == null) {
            failures.add("Paper trading validation report missing");
        } else {
            if (!validationReport.isApprovedForLive()) {
                failures.add("Paper trading validation did not approve live assignment");
            }
            if (validationReport.getRiskBehaviorScore() < minRiskBehaviorScore) {
                failures.add("Risk behavior score below minimum " + minRiskBehaviorScore);
            }
            if (validationReport.getPaperDrawdown() > maxPaperDrawdown) {
                failures.add("Paper drawdown exceeds maximum " + maxPaperDrawdown);
            }
        }

        appendDegradationFailures(record, failures);

        if (!isMarketSessionActive(record.getSymbol())) {
            failures.add("Market session is not active for symbol " + record.getSymbol());
        }

        return failures.isEmpty() ? GateResult.allowed("All live assignment gates passed") : GateResult.blocked(failures);
    }

    private double backtestScore(StrategyLifecycleRecord record) {
        if (record.getRankScore() != null && record.getRankScore().getBacktestScore() > 0.0) {
            return record.getRankScore().getBacktestScore();
        }
        if (record.getLastAIReview() != null) {
            return record.getLastAIReview().getAiConfidence() * 100.0;
        }
        return record.getAssignmentScore();
    }

    private double paperTradingScore(StrategyLifecycleRecord record) {
        StrategyValidationReport report = record.getLastValidationReport();
        if (record.getRankScore() != null && record.getRankScore().getPaperTradingScore() > 0.0) {
            return record.getRankScore().getPaperTradingScore();
        }
        if (report == null) {
            return 0.0;
        }
        return ((Math.min(report.getPaperWinRate(), 1.0) * 45.0)
                + (Math.min(report.getPaperProfitFactor() / 3.0, 1.0) * 45.0)
                + (Math.max(1.0 - Math.min(report.getPaperDrawdown() / maxPaperDrawdown, 1.0), 0.0) * 10.0));
    }

    private void appendDegradationFailures(StrategyLifecycleRecord record, List<String> failures) {
        if (record.getLifecycleStatus() == StrategyLifecycleStatus.DEGRADED
                || record.getLifecycleStatus() == StrategyLifecycleStatus.DEMOTED
                || record.getLifecycleStatus() == StrategyLifecycleStatus.PAUSED
                || record.getLifecycleStatus() == StrategyLifecycleStatus.REPLACED
                || record.getLifecycleStatus() == StrategyLifecycleStatus.ARCHIVED) {
            failures.add("Strategy lifecycle status blocks trading: " + record.getLifecycleStatus());
        }
        if (record.getLastHealthReport() != null) {
            StrategyHealthLevel health = record.getLastHealthReport().getHealthLevel();
            if (health != null && health.requiresIntervention()) {
                failures.add("Strategy health is degraded: " + health);
            }
        }
    }

    private boolean isMarketSessionActive(String symbol) {
        try {
            TradePair pair = TradePair.fromSymbol(symbol);
            return pair.isTradableNow();
        } catch (Exception exception) {
            return false;
        }
    }

    /** Immutable gate decision with human-readable block reasons. */
    @Getter
    @Builder
    public static final class GateResult {
        private final boolean allowed;
        private final List<String> reasons;

        public static GateResult allowed(String reason) {
            return GateResult.builder().allowed(true).reasons(List.of(reason)).build();
        }

        public static GateResult blocked(List<String> reasons) {
            return GateResult.builder().allowed(false).reasons(List.copyOf(reasons)).build();
        }

        public String summary() {
            return String.join(" | ", reasons == null ? List.of() : reasons);
        }
    }
}
