package org.investpro.indicators.providers;

import org.investpro.indicators.ChartIndicator;
import org.investpro.indicators.RSIIndicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class RsiIndicatorProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "RSI";
    }

    @Override
    public String displayName() {
        return "Relative Strength Index";
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
        return "RSI";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("close");
    }

    @Override
    public ChartIndicator create(IndicatorProviderContext context) {
        return new RSIIndicator(context.intConfig("period", 14));
    }
}
