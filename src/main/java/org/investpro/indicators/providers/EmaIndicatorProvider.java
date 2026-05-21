package org.investpro.indicators.providers;

import org.investpro.indicators.ChartIndicator;
import org.investpro.indicators.ExponentialMovingAverageIndicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class EmaIndicatorProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "EMA";
    }

    @Override
    public String displayName() {
        return "Exponential Moving Average";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public String indicatorName() {
        return "EMA";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("close");
    }

    @Override
    public ChartIndicator create(IndicatorProviderContext context) {
        return new ExponentialMovingAverageIndicator(context.intConfig("period", 20));
    }
}
