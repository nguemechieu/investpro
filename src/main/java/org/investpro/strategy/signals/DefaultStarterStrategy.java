package org.investpro.strategy.signals;

import org.investpro.indicators.IndicatorEngine;
import org.investpro.indicators.IndicatorSnapshot;
import org.investpro.market.MarketContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class DefaultStarterStrategy implements TradingStrategy {
    private final IndicatorEngine indicatorEngine = new IndicatorEngine();

    @Override
    public String name() {
        return "Default EMA/RSI/ATR Starter";
    }

    @Override
    public StrategySignal evaluate(MarketContext context) {
        if (context == null || context.latestCandle() == null || context.dataFreshnessStatus() != MarketContext.DataFreshnessStatus.FRESH) {
            return StrategySignal.hold(name(), context == null ? "" : context.symbol(), "Market context is missing or stale.");
        }

        IndicatorSnapshot indicators = indicatorEngine.calculate(context);
        BigDecimal close = context.latestCandle().close();
        BigDecimal atr = indicators.atr();
        BigDecimal confidence = new BigDecimal("0.62");

        if (close.compareTo(indicators.sma()) > 0 && indicators.rsi().compareTo(BigDecimal.valueOf(70)) < 0) {
            return new StrategySignal(name(), context.exchangeId(), context.symbol(), TradingAction.BUY, confidence,
                    "Price is above the moving average and RSI is not overbought.",
                    close.subtract(atr.multiply(BigDecimal.valueOf(2))),
                    close.add(atr.multiply(BigDecimal.valueOf(3))),
                    Duration.ofHours(4), indicators, Instant.now(), Map.of("style", "conservative"));
        }

        if (close.compareTo(indicators.sma()) < 0 && indicators.rsi().compareTo(BigDecimal.valueOf(30)) > 0) {
            return new StrategySignal(name(), context.exchangeId(), context.symbol(), TradingAction.SELL, confidence,
                    "Price is below the moving average and RSI is not oversold.",
                    close.add(atr.multiply(BigDecimal.valueOf(2))),
                    close.subtract(atr.multiply(BigDecimal.valueOf(3))),
                    Duration.ofHours(4), indicators, Instant.now(), Map.of("style", "conservative"));
        }

        return new StrategySignal(name(), context.exchangeId(), context.symbol(), TradingAction.HOLD,
                new BigDecimal("0.40"), "Conditions are unclear.", BigDecimal.ZERO, BigDecimal.ZERO,
                Duration.ZERO, indicators, Instant.now(), Map.of());
    }
}
