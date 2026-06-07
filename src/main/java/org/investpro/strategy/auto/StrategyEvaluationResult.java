package org.investpro.strategy.auto;

import org.investpro.strategy.lab.StrategyPerformanceReport;

import java.util.List;

public record StrategyEvaluationResult(
        StrategyCandidate candidate,
        StrategyPerformanceReport inSampleReport,
        StrategyPerformanceReport outOfSampleReport,
        double score,
        boolean passed,
        List<String> warnings,
        List<String> errors) {
}
