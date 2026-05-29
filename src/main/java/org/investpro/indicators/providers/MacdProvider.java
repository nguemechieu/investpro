package org.investpro.indicators.providers;

import org.investpro.indicators.Indicator;
import org.investpro.indicators.MACD;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class MacdProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "MACD";
    }

    @Override
    public String displayName() {
        return "MACD";
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
        return "MACD";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("close");
    }

    @Override
    public Indicator create(IndicatorProviderContext context) {
        return new MACD(
                context.intConfig("fastPeriod", 12),
                context.intConfig("slowPeriod", 26),
                context.intConfig("signalPeriod", 9));
    }
}
