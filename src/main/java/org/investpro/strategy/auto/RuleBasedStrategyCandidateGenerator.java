package org.investpro.strategy.auto;

import org.investpro.config.AppConfig;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.indicators.metadata.IndicatorDefinitionRegistry;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.rules.CandlePattern;
import org.investpro.strategy.rules.IndicatorCondition;
import org.investpro.strategy.rules.IndicatorConditionOperator;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RuleBasedStrategyCandidateGenerator implements StrategyCandidateGenerator {

    private static final String CFG_MAX_CANDIDATES = "autoStrategy.maxCandidatesPerSymbol";

    @Override
    public List<StrategyCandidate> generateCandidates(StrategyGenerationContext context) {
        StrategyGenerationContext safeContext = context == null
                ? new StrategyGenerationContext("UNKNOWN", Timeframe.H1, List.of(), MarketRegime.UNKNOWN, RiskProfile.conservative(), "")
                : context;
        List<StrategyCandidate> candidates = new ArrayList<>();
        Timeframe timeframe = safeContext.timeframe() == null ? Timeframe.H1 : safeContext.timeframe();

        candidates.add(candidate(safeContext, "RSI Mean Reversion", 74.0,
                indicatorRule(SignalType.BUY, INDICATORS.RSI_REGION_CROSSOVER, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.RSI_REGION_CROSSOVER, timeframe)));
        candidates.add(candidate(safeContext, "MACD Trend Expansion", 72.0,
                indicatorRule(SignalType.BUY, INDICATORS.MACD_SIGNAL, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.MACD_SIGNAL, timeframe)));
        candidates.add(candidate(safeContext, "Bollinger Range Reversion", 69.0,
                indicatorRule(SignalType.BUY, INDICATORS.BOLLINGER_BANDS, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.BOLLINGER_BANDS, timeframe)));
        candidates.add(candidate(safeContext, "EMA Momentum Cross", 67.0,
                indicatorRule(SignalType.BUY, INDICATORS.EMA, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.EMA, timeframe)));
        candidates.add(candidate(safeContext, "Stochastic Reversal", 65.0,
                indicatorRule(SignalType.BUY, INDICATORS.STOCHASTIC_REGION_CROSSOVER, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.STOCHASTIC_REGION_CROSSOVER, timeframe)));
        candidates.add(candidate(safeContext, "ATR Volatility Breakout", 63.0,
                indicatorRule(SignalType.BUY, INDICATORS.ATR, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.ATR, timeframe)));
        candidates.add(candidate(safeContext, "Candle Confirmation Swing", 61.0,
                candleRule(CandlePattern.HAMMER, timeframe),
                candleRule(CandlePattern.ENGULFING_BEARISH, timeframe),
                indicatorRule(SignalType.BUY, INDICATORS.RSI, timeframe),
                indicatorRule(SignalType.SELL, INDICATORS.RSI, timeframe)));

        int limit = Math.max(1, AppConfig.getInt(CFG_MAX_CANDIDATES, 20));
        return candidates.stream().limit(limit).toList();
    }

    private StrategyCandidate candidate(
            StrategyGenerationContext context,
            String name,
            double score,
            StrategyRuleDefinition... rules) {
        String fullName = "%s %s %s".formatted(name, context.symbol(), context.timeframe().getCode());
        StrategyDefinition definition = StrategyDefinition.builder()
                .name(fullName)
                .baseName(name)
                .parameters(StrategyParameters.builder().build())
                .rules(List.of(rules))
                .build();
        return new StrategyCandidate(
                UUID.randomUUID().toString(),
                definition,
                StrategyGenerationSource.RULE_BASED,
                context.symbol(),
                context.marketRegime(),
                score,
                List.of("Generated from InvestPro rule templates for " + context.marketRegime()),
                Instant.now());
    }

    private StrategyRuleDefinition indicatorRule(SignalType signalType, INDICATORS indicator, Timeframe timeframe) {
        return new StrategyRuleDefinition(
                StrategyRuleSource.INDICATOR,
                signalType,
                indicator,
                null,
                timeframe,
                defaultParams(indicator));
    }

    private StrategyRuleDefinition candleRule(CandlePattern pattern, Timeframe timeframe) {
        return new StrategyRuleDefinition(
                StrategyRuleSource.CANDLE_PATTERN,
                pattern.getDefaultSignal(),
                null,
                pattern,
                timeframe,
                Map.of());
    }

    private Map<String, String> defaultParams(INDICATORS indicator) {
        Map<String, String> defaults = new LinkedHashMap<>();
        IndicatorDefinitionRegistry.get(indicator).parameters()
                .forEach(parameter -> defaults.put(parameter.name(), parameter.defaultValue()));
        return defaults;
    }
}
