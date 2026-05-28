package org.investpro.decision;

/**
 * Decision performance timing metrics captured during pipeline execution.
 *
 * <p>Measures wall-clock duration (in nanoseconds) for each phase of the
 * institutional decision pipeline. Used for latency monitoring, optimization,
 * and identifying bottlenecks in Strategy Lab simulations.</p>
 *
 * <p>All durations are in nanoseconds. Use {@link #toMillis(long)} for display.
 * Zero indicates a phase was skipped (e.g., AI reasoning in LIGHTWEIGHT mode).</p>
 *
 * @param decisionGenerationNs  time to generate the {@link TradeIntent}
 * @param riskEvaluationNs      time to complete {@link RiskEvaluation}
 * @param positionSizingNs      time to complete {@link PositionSizingDecision}
 * @param aiReasoningNs         time for AI reasoning (0 if skipped)
 * @param routingNs             time to select {@link ExecutionRoute}
 * @param executionPlanningNs   time to build {@link ExecutionPlan}
 * @param assemblyNs            time to assemble the final decision object
 */
public record DecisionPerformanceMetrics(
        long decisionGenerationNs,
        long riskEvaluationNs,
        long positionSizingNs,
        long aiReasoningNs,
        long routingNs,
        long executionPlanningNs,
        long assemblyNs
) {

    /** Returns total pipeline time in nanoseconds (sum of all phases). */
    public long totalNs() {
        return decisionGenerationNs + riskEvaluationNs + positionSizingNs
                + aiReasoningNs + routingNs + executionPlanningNs + assemblyNs;
    }

    /** Returns total pipeline time in milliseconds (rounded). */
    public long totalMs() {
        return totalNs() / 1_000_000L;
    }

    /** Returns {@code true} if total pipeline time is within the given budget in milliseconds. */
    public boolean isWithinLatencyBudget(long budgetMs) {
        return totalMs() <= budgetMs;
    }

    /** Returns nanoseconds converted to milliseconds (helper for display). */
    public static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    /** Returns the slowest phase name and its duration in milliseconds. */
    public String slowestPhase() {
        long max = 0;
        String name = "none";
        if (decisionGenerationNs > max) { max = decisionGenerationNs; name = "decisionGeneration"; }
        if (riskEvaluationNs     > max) { max = riskEvaluationNs;     name = "riskEvaluation"; }
        if (positionSizingNs     > max) { max = positionSizingNs;     name = "positionSizing"; }
        if (aiReasoningNs        > max) { max = aiReasoningNs;        name = "aiReasoning"; }
        if (routingNs            > max) { max = routingNs;            name = "routing"; }
        if (executionPlanningNs  > max) { max = executionPlanningNs;  name = "executionPlanning"; }
        if (assemblyNs           > max) { max = assemblyNs;           name = "assembly"; }
        return name + " (" + String.format("%.3f", toMillis(max)) + " ms)";
    }

    /** Returns a zero-metrics instance — use when measurements are not available. */
    public static DecisionPerformanceMetrics zero() {
        return new DecisionPerformanceMetrics(0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "DecisionPerformanceMetrics{totalMs=" + totalMs()
                + ", slowest=" + slowestPhase() + "}";
    }
}
