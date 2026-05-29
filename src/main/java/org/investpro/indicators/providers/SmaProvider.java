package org.investpro.indicators.providers;

import org.investpro.indicators.Indicator;
import org.investpro.indicators.SimpleMovingAverage;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class SmaProvider implements IndicatorProvider {
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
    public Indicator create(IndicatorProviderContext context) {
        return new SimpleMovingAverage(context.intConfig("period", 20));
    }
}
