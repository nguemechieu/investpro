package org.investpro.indicators;

import java.util.List;

public final class IndicatorCatalog {
    private IndicatorCatalog() {
    }

    public static List<String> all() {
        return List.of(
                "SMA", "EMA", "WMA", "HMA",
                "RSI", "MACD", "ROC", "STOCHASTIC_K",
                "ATR", "BOLLINGER_BANDS", "KELTNER_CHANNEL", "DONCHIAN_CHANNEL",
                "OBV", "MFI", "VWAP", "MOMENTUM", "WILLIAMS_R",
                "ADX", "PARABOLIC_SAR", "CCI",
                "HIGHEST_HIGH", "LOWEST_LOW", "CROSS_ABOVE", "CROSS_BELOW");
    }
}
