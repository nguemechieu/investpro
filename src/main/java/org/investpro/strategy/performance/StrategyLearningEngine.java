package org.investpro.strategy.performance;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.decision.MarketRegime;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.StrategyLearningProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains and updates learning profiles for strategy assignments.
 * Learns from each trade outcome which market conditions, regimes, and
 * sessions lead to winning vs losing trades.
 */
@Slf4j
public class StrategyLearningEngine {

    private static volatile StrategyLearningEngine instance;

    /** Learning profiles keyed by assignmentId. */
    private final ConcurrentHashMap<String, LearningAccumulator> accumulators =
            new ConcurrentHashMap<>();

    private static final String SOURCE = "StrategyLearningEngine";

    private StrategyLearningEngine() {
        log.info("StrategyLearningEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton StrategyLearningEngine
     */
    public static StrategyLearningEngine getInstance() {
        StrategyLearningEngine local = instance;
        if (local == null) {
            synchronized (StrategyLearningEngine.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyLearningEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Updates the learning profile from a completed trade observation.
     *
     * @param assignmentId    assignment identifier
     * @param strategyId      strategy identifier
     * @param won             true if the trade was profitable
     * @param confidence      signal confidence for this trade (0.0-1.0)
     * @param regime          market regime active during the trade
     * @param session         trading session name (e.g. LONDON, NEW_YORK)
     * @param drawdownAtTime  account drawdown at trade entry (fraction)
     */
    public void learnFromTrade(String assignmentId, String strategyId,
                               boolean won, double confidence,
                               MarketRegime regime, String session,
                               double drawdownAtTime) {
        LearningAccumulator acc = accumulators.computeIfAbsent(assignmentId, k -> new LearningAccumulator(strategyId));
        synchronized (acc) {
            acc.observations++;
            acc.version++;

            String regimeName = regime != null ? regime.name() : "UNKNOWN";

            if (won) {
                acc.winByRegime.merge(regimeName, 1, Integer::sum);
                acc.tradesByRegime.merge(regimeName, 1, Integer::sum);
                if (session != null) acc.winBySession.merge(session, 1, Integer::sum);
                if (session != null) acc.tradesBySession.merge(session, 1, Integer::sum);
                acc.totalWinConfidence += confidence;
                acc.wins++;
            } else {
                acc.tradesByRegime.merge(regimeName, 1, Integer::sum);
                if (session != null) acc.tradesBySession.merge(session, 1, Integer::sum);
                acc.totalLossConfidence += confidence;
                acc.losses++;
                if (drawdownAtTime > 0.08) {
                    acc.drawdownTriggers.add(String.format(
                            "Drawdown %.1f%% during %s regime in %s session",
                            drawdownAtTime * 100, regimeName, session));
                }
            }

            // Update confidence threshold (EMA-like update)
            double winConf = acc.wins > 0 ? acc.totalWinConfidence / acc.wins : 0.50;
            double lossConf = acc.losses > 0 ? acc.totalLossConfidence / acc.losses : 0.50;
            acc.optimalConfidenceThreshold = (winConf + lossConf) / 2.0;

            log.debug("Learning update for assignment={}: regime={}, won={}, obs={}",
                    assignmentId, regimeName, won, acc.observations);
        }

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.STRATEGY_LEARNING_UPDATED, SOURCE, assignmentId));
    }

    /**
     * Returns the current learning profile for an assignment.
     *
     * @param assignmentId the assignment identifier
     * @return StrategyLearningProfile, or a new default profile if none exists
     */
    public StrategyLearningProfile getProfile(String assignmentId) {
        LearningAccumulator acc = accumulators.get(assignmentId);
        if (acc == null) return null;
        synchronized (acc) {
            return buildProfile(assignmentId, acc);
        }
    }

    /**
     * Returns the current profile or initialises a new one.
     *
     * @param assignmentId the assignment identifier
     * @param strategyId   the strategy identifier
     * @return current or freshly created StrategyLearningProfile
     */
    public StrategyLearningProfile computeProfile(String assignmentId, String strategyId) {
        LearningAccumulator acc = accumulators.computeIfAbsent(
                assignmentId, k -> new LearningAccumulator(strategyId));
        synchronized (acc) {
            return buildProfile(assignmentId, acc);
        }
    }

    /**
     * Generates human-readable adaptation notes from a learning profile.
     *
     * @param profile the learning profile
     * @return list of insight strings
     */
    public List<String> generateAdaptationNotes(StrategyLearningProfile profile) {
        List<String> notes = new ArrayList<>();
        if (profile == null) {
            notes.add("Insufficient data for adaptation notes.");
            return notes;
        }
        if (!profile.getBestRegimes().isEmpty()) {
            notes.add("Performs best in regimes: " + String.join(", ", profile.getBestRegimes()));
        }
        if (!profile.getWorstRegimes().isEmpty()) {
            notes.add("Performs worst in regimes: " + String.join(", ", profile.getWorstRegimes()));
        }
        if (!profile.getBestSessions().isEmpty()) {
            notes.add("Best sessions: " + String.join(", ", profile.getBestSessions()));
        }
        if (profile.getOptimalConfidenceThreshold() > 0) {
            notes.add(String.format("Optimal confidence threshold: %.2f",
                    profile.getOptimalConfidenceThreshold()));
        }
        if (profile.getAvgWinConfidence() > profile.getAvgLossConfidence()) {
            notes.add(String.format(
                    "Higher confidence signals win more often (win_conf=%.2f vs loss_conf=%.2f)",
                    profile.getAvgWinConfidence(), profile.getAvgLossConfidence()));
        }
        if (!profile.getDrawdownTriggers().isEmpty()) {
            notes.add("Drawdown triggers observed: "
                    + profile.getDrawdownTriggers().get(profile.getDrawdownTriggers().size() - 1));
        }
        return Collections.unmodifiableList(notes);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private StrategyLearningProfile buildProfile(String assignmentId, LearningAccumulator acc) {
        List<String> bestRegimes = new ArrayList<>();
        List<String> worstRegimes = new ArrayList<>();
        for (Map.Entry<String, Integer> e : acc.tradesByRegime.entrySet()) {
            int trades = e.getValue();
            int wins = acc.winByRegime.getOrDefault(e.getKey(), 0);
            double wr = trades > 0 ? (double) wins / trades : 0.0;
            if (wr >= 0.55 && trades >= 3) bestRegimes.add(e.getKey());
            if (wr < 0.40 && trades >= 3) worstRegimes.add(e.getKey());
        }

        List<String> bestSessions = new ArrayList<>();
        List<String> worstSessions = new ArrayList<>();
        for (Map.Entry<String, Integer> e : acc.tradesBySession.entrySet()) {
            int trades = e.getValue();
            int wins = acc.winBySession.getOrDefault(e.getKey(), 0);
            double wr = trades > 0 ? (double) wins / trades : 0.0;
            if (wr >= 0.55 && trades >= 3) bestSessions.add(e.getKey());
            if (wr < 0.40 && trades >= 3) worstSessions.add(e.getKey());
        }

        double avgWinConf = acc.wins > 0 ? acc.totalWinConfidence / acc.wins : 0.50;
        double avgLossConf = acc.losses > 0 ? acc.totalLossConfidence / acc.losses : 0.50;

        List<String> notes = new ArrayList<>();
        notes.add("Observations: " + acc.observations);
        if (!bestRegimes.isEmpty()) notes.add("Best regimes: " + String.join(", ", bestRegimes));

        return StrategyLearningProfile.builder()
                .assignmentId(assignmentId)
                .strategyId(acc.strategyId)
                .symbol("").timeframe("")
                .bestRegimes(Collections.unmodifiableList(bestRegimes))
                .worstRegimes(Collections.unmodifiableList(worstRegimes))
                .bestSessions(Collections.unmodifiableList(bestSessions))
                .worstSessions(Collections.unmodifiableList(worstSessions))
                .optimalConfidenceThreshold(acc.optimalConfidenceThreshold)
                .avgWinConfidence(avgWinConf)
                .avgLossConfidence(avgLossConf)
                .profitableConditions(Collections.unmodifiableList(bestRegimes))
                .unprofitableConditions(Collections.unmodifiableList(worstRegimes))
                .drawdownTriggers(Collections.unmodifiableList(
                        new ArrayList<>(acc.drawdownTriggers)))
                .learningVersion(acc.version)
                .learningObservations(acc.observations)
                .lastLearnedAt(Instant.now())
                .adaptationNotes(String.join("; ", notes))
                .build();
    }

    // =========================================================================
    // Inner accumulator
    // =========================================================================

    private static final class LearningAccumulator {
        final String strategyId;
        int observations;
        int version;
        int wins;
        int losses;
        double totalWinConfidence;
        double totalLossConfidence;
        double optimalConfidenceThreshold = 0.50;
        final Map<String, Integer> tradesByRegime = new HashMap<>();
        final Map<String, Integer> winByRegime = new HashMap<>();
        final Map<String, Integer> tradesBySession = new HashMap<>();
        final Map<String, Integer> winBySession = new HashMap<>();
        final List<String> drawdownTriggers = new ArrayList<>();

        LearningAccumulator(String strategyId) {
            this.strategyId = strategyId;
        }
    }
}
