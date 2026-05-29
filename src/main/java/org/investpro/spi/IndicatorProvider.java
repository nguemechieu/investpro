package org.investpro.spi;

import org.investpro.indicators.Indicator;

import java.util.Set;

public interface IndicatorProvider extends InvestProPlugin {
    String indicatorName();

    Set<String> supportedInputs();

    Indicator create(IndicatorProviderContext context);
}
