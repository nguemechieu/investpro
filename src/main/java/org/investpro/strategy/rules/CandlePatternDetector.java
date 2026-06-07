package org.investpro.strategy.rules;

import org.investpro.data.CandleData;

import java.util.List;

public interface CandlePatternDetector {
    List<CandlePatternSignal> detect(List<CandleData> candles, CandlePattern pattern);
}
