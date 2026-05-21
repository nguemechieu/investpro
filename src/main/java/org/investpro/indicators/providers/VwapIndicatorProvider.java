package org.investpro.indicators.providers;

import org.investpro.indicators.ChartIndicator;
import org.investpro.indicators.VWAPIndicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class VwapIndicatorProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "VWAP";
    }

    @Override
    public String displayName() {
        return "Volume Weighted Average Price";
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
        return "VWAP";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("high", "low", "close", "volume");
    }

    @Override
    public ChartIndicator create(IndicatorProviderContext context) {
        return new VWAPIndicator();
    }
}
