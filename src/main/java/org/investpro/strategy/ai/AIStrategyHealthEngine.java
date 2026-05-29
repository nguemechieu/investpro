package org.investpro.strategy.ai;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors the health of active strategy assignments using performance metrics.
 *
 * <p><strong>CRITICAL:</strong> This engine is a supervisor/analyst only.
 * It NEVER places orders, bypasses RiskEngine controls, or makes binding lifecycle decisions.
 * Health reports are advisory input to {@link org.investpro.strategy.management.StrategyAssignmentManager}.</p>
 */
@Slf4j
public class AIStrategyHealthEngine {

    private static volatile AIStrategyHealthEngine instance;

    /** Cache of the most recent health report per assignment ID. */
    private final ConcurrentHashMap<String, StrategyHealthReport> lastReports =
            new ConcurrentHashMap<>();

    private static final String SOURCE = "AIStrategyHealthEngine";

    private AIStrategyHealthEngine() {
        log.info("AIStrategyHealthEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton AIStrategyHealthEngine
     */
    public static AIStrategyHealthEngine getInstance() {
        AIStrategyHealthEngine local = instance;
        if (local == null) {
            synchronized (AIStrategyHealthEngine.class) {
                local = instance;
                if (local == null) {
                    local = new AIStrategyHealthEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Assesses the health of an active strategy assignment.
     *
     * @param record  the lifecycle record for the assignment
     * @param metrics the latest performance metrics
     * @return StrategyHealthReport with health level, score, and AI explanation
     */
    public StrategyHealthReport assessHealth(
            StrategyLifecycleRecord record,
            StrategyPerformanceMetrics metrics) {

        if (record == null || metrics == null) {
            log.warn("assessHealth called with null input");
            return null;
        }

        log.debug("Assessing health for assignment={}", record.getAssignmentId());

        StrategyHealthReport previous = lastReports.get(record.getAssignmentId());
        StrategyHealthLevel previousLevel = previous != null ? previous.getHealthLevel() : null;

        // --- Score computation (0-100) ---
        double score = 0.0;
        List<String> alerts = new ArrayList<>();

        // Win rate: 25 pts (full marks at >= 60%)
        double wrScore = Math.min(metrics.getWinRate() / 0.60, 1.0) * 25.0;
        score += wrScore;
        if (metrics.getWinRate() < 0.40) {
            alerts.add(String.format("Win rate critically low: %.1f%%", metrics.getWinRate() * 100));
        }

        // Profit factor: 20 pts (full marks at >= 2.0)
        double pfScore = Math.min(metrics.getProfitFactor() / 2.0, 1.0) * 20.0;
        score += pfScore;
        if (metrics.getProfitFactor() < 1.0) {
            alerts.add(String.format("Profit factor below 1.0: %.2f (net loss)", metrics.getProfitFactor()));
        }

        // Drawdown penalty: -20 pts at > 15% drawdown
        double ddPenalty = 0.0;
        if (metrics.getMaxDrawdown() > 0.05) {
            ddPenalty = Math.min(metrics.getMaxDrawdown() / 0.15, 1.0) * 20.0;
            score -= ddPenalty;
            if (metrics.getMaxDrawdown() > 0.12) {
                alerts.add(String.format("Drawdown elevated: %.1f%%", metrics.getMaxDrawdown() * 100));
            }
        } else {
            score += 20.0; // no drawdown = full credit
        }

        // Expectancy: 15 pts (positive = full)
        if (metrics.getExpectancy() > 0) {
            score += 15.0;
        } else {
            alerts.add("Negative expectancy: strategy losing money on average per trade");
        }

        // Sharpe: 10 pts (full at >= 2.0)
        double sharpeScore = Math.min(Math.max(metrics.getSharpeRatio(), 0.0) / 2.0, 1.0) * 10.0;
        score += sharpeScore;

        // Sortino: 10 pts (full at >= 2.0)
        double sortinoScore = Math.min(Math.max(metrics.getSortinoRatio(), 0.0) / 2.0, 1.0) * 10.0;
        score += sortinoScore;

        score = Math.max(Math.min(score, 100.0), 0.0);

        // --- Health level ---
        StrategyHealthLevel level;
        if (score >= 85) level = StrategyHealthLevel.EXCELLENT;
        else if (score >= 70) level = StrategyHealthLevel.GOOD;
        else if (score >= 55) level = StrategyHealthLevel.WATCH;
        else if (score >= 40) level = StrategyHealthLevel.DEGRADED;
        else level = StrategyHealthLevel.CRITICAL;

        if (level == StrategyHealthLevel.CRITICAL) {
            alerts.add("CRITICAL: strategy requires immediate review");
        } else if (level == StrategyHealthLevel.DEGRADED) {
            alerts.add("DEGRADED: strategy performance below acceptable thresholds");
        }

        // --- AI explanation ---
        String explanation = buildExplanation(record, metrics, level, previousLevel, score, alerts);

        // --- Recommended action ---
        AIReplacementRecommendation recommendation;
        if (level == StrategyHealthLevel.CRITICAL) {
            recommendation = AIReplacementRecommendation.REPLACE;
        } else if (level == StrategyHealthLevel.DEGRADED) {
            recommendation = AIReplacementRecommendation.DEMOTE;
        } else if (level == StrategyHealthLevel.WATCH) {
            recommendation = AIReplacementRecommendation.KEEP;
        } else {
            recommendation = AIReplacementRecommendation.KEEP;
        }

        boolean regimeShifting = metrics.getRegimePerformance() != null
                && metrics.getRegimePerformance().values().stream().anyMatch(v -> v < 0.30);

        StrategyHealthReport report = StrategyHealthReport.builder()
                .reportId(UUID.randomUUID().toString())
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .healthLevel(level)
                .previousHealthLevel(previousLevel)
                .winRate(metrics.getWinRate())
                .profitFactor(metrics.getProfitFactor())
                .maxDrawdown(metrics.getMaxDrawdown())
                .expectancy(metrics.getExpectancy())
                .sharpeRatio(metrics.getSharpeRatio())
                .sortinoRatio(metrics.getSortinoRatio())
                .recentWinRate(metrics.getWinRate())   // Tracker will provide window-specific value
                .recentProfitFactor(metrics.getProfitFactor())
                .healthScore(score)
                .aiExplanation(explanation)
                .triggeredAlerts(alerts)
                .recommendedAction(recommendation)
                .isRegimeShifting(regimeShifting)
                .generatedAt(Instant.now())
                .build();

        // Fire event only when health level changes
        if (previousLevel != null && previousLevel != level) {
            log.info("Health change for assignment={}: {}->{}  score={}",
                    record.getAssignmentId(), previousLevel, level, String.format("%.1f", score));
            EventBusManager.getInstance().publish(
                    AgentEvent.of(AgentEvent.STRATEGY_HEALTH_CHANGED, SOURCE, report));
            if (level == StrategyHealthLevel.CRITICAL || level == StrategyHealthLevel.DEGRADED) {
                EventBusManager.getInstance().publish(
                        AgentEvent.of(AgentEvent.STRATEGY_DEGRADED, SOURCE, report));
            }
        }

        lastReports.put(record.getAssignmentId(), report);
        return report;
    }

    /**
     * Returns the most recent health report for an assignment.
     *
     * @param assignmentId the assignment identifier
     * @return last StrategyHealthReport, or null if none exists
     */
    public StrategyHealthReport getLastReport(String assignmentId) {
        return lastReports.get(assignmentId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String buildExplanation(StrategyLifecycleRecord record,
                                    StrategyPerformanceMetrics metrics,
                                    StrategyHealthLevel level,
                                    StrategyHealthLevel previousLevel,
                                    double score,
                                    List<String> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Health assessment for %s [%s/%s]: score=%.1f, level=%s",
                record.getStrategyId(), record.getSymbol(), record.getTimeframe(), score, level));
        if (previousLevel != null && previousLevel != level) {
            sb.append(String.format(" (changed from %s)", previousLevel));
        }
        sb.append(String.format(". WR=%.1f%%, PF=%.2f, DD=%.1f%%, Exp=%.4f, Sharpe=%.2f",
                metrics.getWinRate() * 100, metrics.getProfitFactor(),
                metrics.getMaxDrawdown() * 100, metrics.getExpectancy(), metrics.getSharpeRatio()));
        if (!alerts.isEmpty()) {
            sb.append(". Alerts: ").append(String.join("; ", alerts));
        }
        return sb.toString();
    }
}
