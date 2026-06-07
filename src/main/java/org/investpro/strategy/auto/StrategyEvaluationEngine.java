package org.investpro.strategy.auto;

import org.investpro.config.AppConfig;
import org.investpro.data.CandleData;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.lab.StrategyBacktestRequest;
import org.investpro.strategy.lab.StrategyBacktestRunner;
import org.investpro.strategy.lab.StrategyPerformanceReport;

import java.util.ArrayList;
import java.util.List;

public class StrategyEvaluationEngine {

    private final StrategyCandidateValidator validator;
    private final StrategyBacktestRunner runner;

    public StrategyEvaluationEngine() {
        this(new StrategyCandidateValidator(), new StrategyBacktestRunner());
    }

    public StrategyEvaluationEngine(StrategyCandidateValidator validator, StrategyBacktestRunner runner) {
        this.validator = validator;
        this.runner = runner;
    }

    public StrategyEvaluationResult evaluate(StrategyCandidate candidate, StrategyGenerationContext context) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        StrategyValidationResult validation = validator.validate(candidate);
        warnings.addAll(validation.warnings());
        if (!validation.valid()) {
            errors.addAll(validation.errors());
            return new StrategyEvaluationResult(candidate, null, null, 0.0, false, warnings, errors);
        }
        if (context == null || context.candles().size() < 60) {
            errors.add("At least 60 candles are required to evaluate a candidate.");
            return new StrategyEvaluationResult(candidate, null, null, 0.0, false, warnings, errors);
        }

        StrategyRegistry.getInstance().registerDefinition(candidate.strategyDefinition());
        int splitIndex = Math.max(50, (int) Math.floor(context.candles().size() * 0.70));
        splitIndex = Math.min(splitIndex, context.candles().size() - 10);
        StrategyPerformanceReport inSample = run(candidate, context, context.candles().subList(0, splitIndex));
        StrategyPerformanceReport outOfSample = run(candidate, context, context.candles().subList(splitIndex, context.candles().size()));

        double score = score(inSample, outOfSample);
        boolean passed = passesThresholds(inSample, outOfSample, score);
        return new StrategyEvaluationResult(candidate, inSample, outOfSample, score, passed, warnings, errors);
    }

    private StrategyPerformanceReport run(StrategyCandidate candidate, StrategyGenerationContext context, List<CandleData> candles) {
        return runner.run(StrategyBacktestRequest.builder()
                .symbol(context.symbol())
                .timeframe(context.timeframe())
                .strategyName(candidate.strategyDefinition().getName())
                .strategyDefinition(candidate.strategyDefinition())
                .candles(List.copyOf(candles))
                .build());
    }

    private double score(StrategyPerformanceReport inSample, StrategyPerformanceReport outOfSample) {
        if (inSample == null || outOfSample == null) {
            return 0.0;
        }
        return (inSample.getScore() * 0.45)
                + (outOfSample.getScore() * 0.55)
                + Math.max(0.0, outOfSample.getProfitFactor() - 1.0) * 8.0
                - Math.max(0.0, outOfSample.getMaxDrawdown() * 100.0);
    }

    private boolean passesThresholds(StrategyPerformanceReport inSample, StrategyPerformanceReport outOfSample, double score) {
        if (inSample == null || outOfSample == null) {
            return false;
        }
        int minTrades = AppConfig.getInt("autoStrategy.minTrades", 30);
        double minProfitFactor = AppConfig.getDouble("autoStrategy.minProfitFactor", 1.20);
        double maxDrawdownPercent = AppConfig.getDouble("autoStrategy.maxDrawdownPercent", 15.0);
        boolean requireOutOfSample = AppConfig.getBoolean("autoStrategy.requireOutOfSamplePass", true);
        boolean outOfSamplePass = !requireOutOfSample
                || (outOfSample.getTotalTrades() >= Math.max(3, minTrades / 3)
                && outOfSample.getProfitFactor() >= minProfitFactor
                && outOfSample.getMaxDrawdown() * 100.0 <= maxDrawdownPercent);
        return inSample.getTotalTrades() + outOfSample.getTotalTrades() >= minTrades
                && outOfSamplePass
                && score > 0.0;
    }
}
