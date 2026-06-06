package org.investpro.strategy;

import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedStrategyAiTest {

    @Test
    void aiHybridGeneratesReviewedSignalInsteadOfStubHold() throws Exception {
        UnifiedStrategy strategy = new UnifiedStrategy("AI Hybrid");

        StrategySignal signal = strategy.generateSignal(bullishContext());

        assertThat(signal.getReason()).doesNotContain("requires AI service");
        assertThat(signal.getStrategyName()).isEqualTo("AI Hybrid");
        assertThat(signal.getMetadata()).containsKey("aiDecision");
    }

    @Test
    void mlModelGeneratesModelSignalInsteadOfStubHold() throws Exception {
        UnifiedStrategy strategy = new UnifiedStrategy("ML Model");

        StrategySignal signal = strategy.generateSignal(bullishContext());

        assertThat(signal.getReason()).doesNotContain("requires AI service");
        assertThat(signal.getStrategyName()).isEqualTo("ML Model");
        assertThat(signal.getMetadata()).containsKey("mlScore");
    }

    @Test
    void enabledStrategiesIncludeAllCoreFamilies() {
        StrategyRegistry registry = new StrategyRegistry();

        List<String> names = registry.getEnabledStrategies().stream()
                .map(strategy -> String.valueOf(strategy.getName()))
                .toList();

        assertThat(names).contains("AI Hybrid", "ML Model", "Trend Following");
        assertThat(registry.getStrategy("Mean Reversion")).isNotNull();
        assertThat(registry.getStrategy("Breakout")).isNotNull();
    }

    private StrategyContext bullishContext() throws Exception {
        TradePair pair = TradePair.fromSymbol("BTC-USD");
        List<CandleData> candles = bullishCandles();
        double last = candles.getLast().closePrice();
        return StrategyContext.builder()
                .symbol(pair)
                .timeframe(Timeframe.H1)
                .candles(candles)
                .currentPrice(last)
                .bid(last - 0.5)
                .ask(last + 0.5)
                .barsAvailable(candles.size())
                .averageVolume(2_000_000)
                .volatility(0.01)
                .build();
    }

    private List<CandleData> bullishCandles() {
        List<CandleData> candles = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < 140; i++) {
            double open = price;
            price += 1.0 + (i % 7) * 0.04;
            double close = price;
            candles.add(new CandleData(open, close, close + 1.0, open - 0.5, i * 3600, 2_000_000 + i * 1_000));
        }
        return candles;
    }
}
