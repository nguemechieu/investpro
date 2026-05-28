package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.List;

/**
 * On-Balance Volume (OBV) Indicator
 * Momentum indicator that uses volume flow to predict changes in price.
 * Cumulative total volume where volume is added when price goes up and
 * subtracted when it goes down.
 */
@Getter
@Setter
public class OBVIndicator extends BaseIndicator {

    public OBVIndicator() {
        super("OBV", 1);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        double[] obvValues = new double[candles.size()];
        double obv = 0;

        for (int i = 0; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            if (i == 0) {
                obv = candle.volume();
            } else {
                CandleData prevCandle = candles.get(i - 1);
                if (candle.closePrice() > prevCandle.closePrice()) {
                    obv += candle.volume();
                } else if (candle.closePrice() < prevCandle.closePrice()) {
                    obv -= candle.volume();
                }
            }
            obvValues[i] = obv;
        }

        values.put("OBV", obvValues);
        calculated = true;
    }
}
