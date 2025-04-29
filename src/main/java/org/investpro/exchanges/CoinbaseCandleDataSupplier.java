package org.investpro.exchanges;

import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.CandleData;
import org.investpro.CandleDataSupplier;
import org.investpro.TradePair;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

public class CoinbaseCandleDataSupplier extends CandleDataSupplier {
    public CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
    }


    @Override
    public Future<List<CandleData>> get() {
        return null;
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        return Set.of();
    }
}
