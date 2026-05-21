package org.investpro.indicators.providers;

import org.investpro.indicators.ChartIndicator;
import org.investpro.indicators.SimpleMovingAverageIndicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class SmaIndicatorProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "SMA";
    }

    @Override
    public String displayName() {
        return "Simple Moving Average";
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
        return "SMA";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("close");
    }

    @Override
    public ChartIndicator create(IndicatorProviderContext context) {
        return new SimpleMovingAverageIndicator(context.intConfig("period", 20));
    }
}
