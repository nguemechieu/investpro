package org.investpro.strategy.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyLifecycleStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Analyzes overall portfolio exposure across all active strategy assignments.
 * Computes concentration risk, correlation risk, and generates advisory
 * recommendations for portfolio rebalancing.
 *
 * <p><strong>CRITICAL:</strong> This engine is purely advisory. It NEVER
 * places, modifies, or cancels any orders.</p>
 */
@Slf4j
public class PortfolioIntelligenceEngine {

    private static volatile PortfolioIntelligenceEngine instance;

    private final ConcurrentHashMap<String, PortfolioIntelligenceReport> lastReports =
            new ConcurrentHashMap<>();

    private static final String SOURCE = "PortfolioIntelligenceEngine";

    /** Maximum acceptable correlation between two active strategies (above = warning). */
    private static final double MAX_CORRELATION_THRESHOLD = 0.70;

    /** Maximum acceptable single-symbol concentration (fraction of total equity). */
    private static final double MAX_CONCENTRATION_THRESHOLD = 0.30;

    private PortfolioIntelligenceEngine() {
        log.info("PortfolioIntelligenceEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton PortfolioIntelligenceEngine
     */
    public static PortfolioIntelligenceEngine getInstance() {
        PortfolioIntelligenceEngine local = instance;
        if (local == null) {
            synchronized (PortfolioIntelligenceEngine.class) {
                local = instance;
                if (local == null) {
                    local = new PortfolioIntelligenceEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Analyzes the current portfolio across all provided lifecycle records.
     *
     * @param records    all active strategy lifecycle records
     * @param totalEquity total account equity
     * @return PortfolioIntelligenceReport with advisory recommendations
     */
    public PortfolioIntelligenceReport analyze(List<StrategyLifecycleRecord> records, double totalEquity) {
        if (records == null || records.isEmpty()) {
            return buildEmptyReport(totalEquity);
        }

        List<StrategyLifecycleRecord> active = records.stream()
                .filter(r -> r.getLifecycleStatus() != null
                        && (r.getLifecycleStatus().isActive() || r.getLifecycleStatus().isLive()))
                .collect(Collectors.toList());

        // --- Symbol concentration ---
        Map<String, Integer> symbolCounts = new LinkedHashMap<>();
        for (StrategyLifecycleRecord rec : active) {
            symbolCounts.merge(rec.getSymbol(), 1, Integer::sum);
        }

        Map<String, Double> concentrationBySymbol = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : symbolCounts.entrySet()) {
            double fraction = active.isEmpty() ? 0.0 : (double) entry.getValue() / active.size();
            concentrationBySymbol.put(entry.getKey(), fraction);
        }

        double maxConcentration = concentrationBySymbol.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0.0);

        // --- Timeframe diversification ---
        Set<String> timeframes = active.stream()
                .map(StrategyLifecycleRecord::getTimeframe)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // --- Strategy diversification ---
        Set<String> strategies = active.stream()
                .map(StrategyLifecycleRecord::getStrategyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // --- Risk scores ---
        double avgAiConfidence = active.stream()
                .mapToDouble(r -> r.getAiConfidence())
                .average().orElse(0.0);

        double avgHealth = active.stream()
                .mapToDouble(r -> r.getLastHealthReport() != null
                        ? r.getLastHealthReport().getCompositeHealthScore() : 50.0)
                .average().orElse(50.0);

        // --- Recommendations ---
        List<String> recommendations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (maxConcentration > MAX_CONCENTRATION_THRESHOLD) {
            String mostConcentrated = concentrationBySymbol.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("UNKNOWN");
            warnings.add(String.format(
                    "High concentration in %s (%.0f%% of active strategies)",
                    mostConcentrated, maxConcentration * 100));
            recommendations.add("Diversify across more symbols to reduce concentration risk");
        }

        if (timeframes.size() < 2 && active.size() > 2) {
            warnings.add("All active strategies operate on the same timeframe");
            recommendations.add("Add strategies with different timeframes for better diversification");
        }

        if (strategies.size() < 2 && active.size() > 2) {
            warnings.add("Portfolio is concentrated in a single strategy type");
            recommendations.add("Add complementary strategies with different trading logic");
        }

        if (avgAiConfidence < 0.55) {
            warnings.add(String.format("Average AI confidence is low (%.0f%%)", avgAiConfidence * 100));
            recommendations.add("Review strategy assignments — overall AI approval confidence is below threshold");
        }

        if (avgHealth < 40.0) {
            warnings.add(String.format("Average portfolio health score is %.0f/100", avgHealth));
            recommendations.add("Consider demoting or replacing low-health strategies");
        }

        long liveCount = records.stream()
                .filter(r -> r.getLifecycleStatus() == StrategyLifecycleStatus.LIVE_ACTIVE)
                .count();
        long degradedCount = records.stream()
                .filter(r -> r.getLifecycleStatus() == StrategyLifecycleStatus.DEGRADED)
                .count();

        boolean concentrationRisk = maxConcentration > MAX_CONCENTRATION_THRESHOLD;
        boolean correlationRisk = strategies.size() < 2 && active.size() > 2;

        PortfolioIntelligenceReport report = PortfolioIntelligenceReport.builder()
                .totalEquity(totalEquity)
                .activeStrategies(active.size())
                .liveStrategies((int) liveCount)
                .degradedStrategies((int) degradedCount)
                .symbolCount(symbolCounts.size())
                .timeframeCount(timeframes.size())
                .strategyTypeCount(strategies.size())
                .concentrationBySymbol(Collections.unmodifiableMap(concentrationBySymbol))
                .maxConcentration(maxConcentration)
                .concentrationRisk(concentrationRisk)
                .correlationRisk(correlationRisk)
                .avgAiConfidence(avgAiConfidence)
                .avgHealthScore(avgHealth)
                .recommendations(Collections.unmodifiableList(recommendations))
                .warnings(Collections.unmodifiableList(warnings))
                .analyzedAt(Instant.now())
                .build();

        lastReports.put("GLOBAL", report);

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.PORTFOLIO_ANALYZED, SOURCE, report));

        log.info("Portfolio analysis: active={} symbols={} timeframes={} strategies={} warnings={}",
                active.size(), symbolCounts.size(), timeframes.size(),
                strategies.size(), warnings.size());

        return report;
    }

    /**
     * Returns the most recent portfolio analysis report.
     *
     * @return Optional containing the last report, or empty if none computed yet
     */
    public Optional<PortfolioIntelligenceReport> getLastReport() {
        return Optional.ofNullable(lastReports.get("GLOBAL"));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private PortfolioIntelligenceReport buildEmptyReport(double totalEquity) {
        return PortfolioIntelligenceReport.builder()
                .totalEquity(totalEquity)
                .concentrationBySymbol(Map.of())
                .recommendations(List.of())
                .warnings(List.of())
                .analyzedAt(Instant.now())
                .build();
    }
}
