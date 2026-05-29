package org.investpro.indicators.providers;

import org.investpro.indicators.Indicator;
import org.investpro.indicators.RSI;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class RsiProvider implements IndicatorProvider {
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
    public Indicator create(IndicatorProviderContext context) {
        return new RSI(context.intConfig("period", 14));
    }
}
