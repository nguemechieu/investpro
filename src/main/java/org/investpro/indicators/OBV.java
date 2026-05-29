package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * On-Balance Volume (OBV) Indicator
 * Momentum indicator that uses volume flow to predict changes in price.
 * Cumulative total volume where volume is added when price goes up and
 * subtracted when it goes down.
 */
@Getter
@Setter
public class OBV extends BaseIndicator {

    public OBV() {
        super("OBV", 1);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        List<Double> obvValues = new ArrayList<>();
        double obv = 0;

        for (CandleData candle : candles) {
            if (candles.indexOf(candle) == 0) {
                // First candle: use volume as starting point
                obv = candle.volume();
            } else {
                CandleData prevCandle = candles.get(candles.indexOf(candle) - 1);
                // Add volume if price went up, subtract if it went down
                if (candle.closePrice() > prevCandle.closePrice()) {
                    obv += candle.volume();
                } else if (candle.closePrice() < prevCandle.closePrice()) {
                    obv -= candle.volume();
                }
                // If close equals previous close, OBV stays the same
            }
            obvValues.add(obv);
        }

        double[] obvArray = new double[obvValues.size()];
        for (int i = 0; i < obvValues.size(); i++) {
            obvArray[i] = obvValues.get(i);
        }

        values.put("OBV", obvArray);
        calculated = true;
    }
}
