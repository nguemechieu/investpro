package org.investpro.strategy.lab;

import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyBacktestRunnerRuleSignalTest {

    @Test
    void macdSignalRuleCreatesBacktestTradesOnOneHourCandles() {
        String strategyName = "test-macd-signal-rule";
        StrategyDefinition definition = StrategyDefinition.builder()
                .name(strategyName)
                .baseName("MACD Trend")
                .parameters(StrategyParameters.builder().build())
                .rules(List.of(new StrategyRuleDefinition(
                        StrategyRuleSource.INDICATOR,
                        SignalType.BUY,
                        INDICATORS.MACD_SIGNAL,
                        null,
                        Timeframe.H1,
                        Map.of("fastPeriod", "12", "slowPeriod", "26", "signalPeriod", "9"))))
                .build();

        StrategyCatalog.registerRuntimeDefinition(definition);
        StrategyRegistry.getInstance().registerDefinition(definition);

        StrategyPerformanceReport report = new StrategyBacktestRunner().run(StrategyBacktestRequest.builder()
                .strategyName(strategyName)
                .strategyDefinition(definition)
                .symbol("BTC/USD")
                .timeframe(Timeframe.H1)
                .candles(oscillatingCandles(180))
                .maxTrades(5)
                .build());

        assertNotNull(report);
        assertTrue(report.getTotalTrades() > 0, "MACD_SIGNAL rule should create at least one backtest trade");
    }

    private static List<CandleData> oscillatingCandles(int count) {
        List<CandleData> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = 100.0 + Math.sin(i / 4.0) * 6.0 + Math.sin(i / 13.0) * 2.0;
            double open = close - Math.cos(i / 5.0);
            double high = Math.max(open, close) + 1.2;
            double low = Math.min(open, close) - 1.2;
            candles.add(new CandleData(open, close, high, low, i * Timeframe.H1.getSeconds(), 1_000 + i));
        }
        return candles;
    }
}
