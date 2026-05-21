package org.investpro.spi;

import org.investpro.indicators.ChartIndicator;

import java.util.Set;

public interface IndicatorProvider extends InvestProPlugin {
    String indicatorName();

    Set<String> supportedInputs();

    ChartIndicator create(IndicatorProviderContext context);
}
