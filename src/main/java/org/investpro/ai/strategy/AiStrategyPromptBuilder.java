package org.investpro.ai.strategy;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.rules.CandlePattern;
import org.investpro.strategy.rules.IndicatorConditionOperator;
import org.investpro.strategy.rules.SignalType;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class AiStrategyPromptBuilder {

    private AiStrategyPromptBuilder() {
    }

    public static String build(AiStrategyGenerationRequest request) {
        String userPrompt = request == null ? "" : safe(request.prompt());
        String symbol = request == null || request.optionalPair().isEmpty()
                ? "the selected market"
                : request.optionalPair().get().toString();
        String timeframe = request == null || request.optionalTimeframe().isEmpty()
                ? "the selected timeframe"
                : request.optionalTimeframe().get().getCode();

        return """
                You are helping InvestPro draft a strategy for review and backtesting only.
                Never claim the strategy should trade live. Never bypass validation, risk checks, or user approval.

                Market: %s
                Timeframe: %s
                User request:
                %s

                Return only valid JSON with this shape:
                {
                  "name": "short strategy name",
                  "description": "one paragraph",
                  "rules": [
                    {
                      "source": "INDICATOR or CANDLE_PATTERN",
                      "signalType": "BUY or SELL",
                      "indicator": "RSI",
                      "candlePattern": null,
                      "timeframe": "H1",
                      "parameters": {"period": "14"},
                      "conditions": [
                        {
                          "outputKey": "rsi",
                          "operator": "LESS_THAN",
                          "compareValue": 30,
                          "compareOutputKey": null
                        }
                      ]
                    }
                  ]
                }

                Allowed signal types: %s
                Allowed timeframes: %s
                Allowed indicators: %s
                Allowed candle patterns: %s
                Allowed condition operators: %s
                Use 2 to 6 enabled rules. Include at least one BUY rule and one SELL rule when possible.
                """.formatted(
                symbol,
                timeframe,
                userPrompt,
                names(SignalType.values()),
                names(Timeframe.values()),
                names(INDICATORS.values()),
                names(CandlePattern.values()),
                names(IndicatorConditionOperator.values()));
    }

    private static String names(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
