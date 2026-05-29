package org.investpro.indicators.providers;

import org.investpro.indicators.ATR;
import org.investpro.indicators.Indicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class AtrProvider implements IndicatorProvider {
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
    public Indicator create(IndicatorProviderContext context) {
        return new ATR(context.intConfig("period", 14));
    }
}
