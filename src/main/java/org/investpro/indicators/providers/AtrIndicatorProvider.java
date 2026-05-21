package org.investpro.indicators.providers;

import org.investpro.indicators.ATRIndicator;
import org.investpro.indicators.ChartIndicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class AtrIndicatorProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "ATR";
    }

    @Override
    public String displayName() {
        return "Average True Range";
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
        return "ATR";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("high", "low", "close");
    }

    @Override
    public ChartIndicator create(IndicatorProviderContext context) {
        return new ATRIndicator(context.intConfig("period", 14));
    }
}
