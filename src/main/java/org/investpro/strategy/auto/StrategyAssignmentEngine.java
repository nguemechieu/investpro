package org.investpro.strategy.auto;

import org.investpro.config.AppConfig;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategySelectionService;

import java.util.ArrayList;
import java.util.List;

public class StrategyAssignmentEngine {

    public StrategyAssignmentDecision decide(
            StrategyEvaluationResult best,
            StrategyAssignment currentAssignment,
            StrategyGenerationContext context,
            boolean userApproved) {
        List<String> warnings = new ArrayList<>();
        if (best == null || best.candidate() == null || best.candidate().strategyDefinition() == null) {
            return rejected("", "", "", 0.0, 0.0, "No evaluated candidate is available.", warnings);
        }
        double minImprovementScore = AppConfig.getDouble("autoStrategy.minImprovementScore", 10.0);
        double maxDrawdownPercent = AppConfig.getDouble("autoStrategy.maxDrawdownPercent", 15.0);
        boolean allowLiveAutoAssignment = AppConfig.getBoolean("autoStrategy.allowLiveAutoAssignment", false);
        double currentScore = currentAssignment == null ? 0.0 : currentAssignment.getScoreAtAssignment();
        String symbol = context == null ? "" : context.symbol();
        String currentStrategy = currentAssignment == null ? "" : currentAssignment.getStrategyId();
        String selectedStrategy = best.candidate().strategyDefinition().getName();

        if (!best.passed()) {
            return rejected(symbol, currentStrategy, selectedStrategy, currentScore, best.score(), "Best candidate did not pass evaluation thresholds.", warnings);
        }
        if (best.score() - currentScore < minImprovementScore) {
            return rejected(symbol, currentStrategy, selectedStrategy, currentScore, best.score(), "Best candidate improvement score is below threshold.", warnings);
        }
        if (best.outOfSampleReport() != null && best.outOfSampleReport().getMaxDrawdown() * 100.0 > maxDrawdownPercent) {
            return rejected(symbol, currentStrategy, selectedStrategy, currentScore, best.score(), "Best candidate exceeds max drawdown threshold.", warnings);
        }
        if (!allowLiveAutoAssignment && !userApproved) {
            warnings.add("Live auto assignment is disabled. User approval is required.");
            return new StrategyAssignmentDecision(
                    symbol,
                    currentStrategy,
                    selectedStrategy,
                    false,
                    false,
                    selectedStrategy,
                    currentScore,
                    best.score(),
                    "Awaiting user approval before assignment.",
                    warnings,
                    null);
        }
        if (context == null || context.symbol() == null || context.timeframe() == null) {
            return rejected(symbol, currentStrategy, selectedStrategy, currentScore, best.score(), "Symbol and timeframe are required before assignment.", warnings);
        }

        StrategyRegistry.getInstance().registerDefinition(best.candidate().strategyDefinition());
        StrategyAssignment assignment = StrategySelectionService.getInstance().manuallyAssign(
                context.symbol(),
                context.timeframe(),
                best.candidate().strategyDefinition().getName(),
                false,
                "Auto Strategy Lab selected after evaluation",
                best.score());
        return new StrategyAssignmentDecision(
                context.symbol(),
                currentStrategy,
                selectedStrategy,
                true,
                allowLiveAutoAssignment || userApproved,
                selectedStrategy,
                currentScore,
                best.score(),
                "Assigned candidate for " + context.symbol() + " " + context.timeframe().getCode(),
                warnings,
                assignment);
    }

    private StrategyAssignmentDecision rejected(
            String symbol,
            String currentStrategyName,
            String selectedStrategyName,
            double currentScore,
            double selectedScore,
            String reason,
            List<String> warnings) {
        return new StrategyAssignmentDecision(
                symbol,
                currentStrategyName,
                selectedStrategyName,
                false,
                false,
                selectedStrategyName,
                currentScore,
                selectedScore,
                reason,
                warnings == null ? List.of() : List.copyOf(warnings),
                null);
    }
}
