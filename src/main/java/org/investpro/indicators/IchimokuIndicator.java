package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.List;

/**
 * Ichimoku Cloud Indicator
 * Comprehensive indicator showing support, resistance, trend direction, and momentum.
 */
public class IchimokuIndicator extends BaseIndicator {
    
    private final int conversionPeriod;
    private final int basePeriod;
    private final int leadingSpanPeriod;
    
    public IchimokuIndicator() {
        this(9, 26, 52);
    }
    
    public IchimokuIndicator(int conversionPeriod, int basePeriod, int leadingSpanPeriod) {
        super("Ichimoku", basePeriod);
        this.conversionPeriod = conversionPeriod;
        this.basePeriod = basePeriod;
        this.leadingSpanPeriod = leadingSpanPeriod;
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < basePeriod) {
            return;
        }
        
        double[] tenkanSen = calculateTenkanSen(candles);
        double[] kijunSen = calculateKijunSen(candles);
        double[] senkouSpanA = calculateSenkouSpanA(tenkanSen, kijunSen);
        double[] senkouSpanB = calculateSenkouSpanB(candles);
        double[] chikouSpan = calculateChikouSpan(candles);
        
        values.put("TenkanSen", tenkanSen);
        values.put("KijunSen", kijunSen);
        values.put("SenkouSpanA", senkouSpanA);
        values.put("SenkouSpanB", senkouSpanB);
        values.put("ChikouSpan", chikouSpan);
        calculated = true;
    }
    
    private double[] calculateTenkanSen(List<CandleData> candles) {
        return calculateHighLowMiddle(candles, conversionPeriod);
    }
    
    private double[] calculateKijunSen(List<CandleData> candles) {
        return calculateHighLowMiddle(candles, basePeriod);
    }
    
    private double[] calculateHighLowMiddle(List<CandleData> candles, int period) {
        double[] result = new double[candles.size()];
        
        for (int i = period - 1; i < candles.size(); i++) {
            double highest = Double.NEGATIVE_INFINITY;
            double lowest = Double.POSITIVE_INFINITY;
            
            for (int j = i - period + 1; j <= i; j++) {
                CandleData candle = candles.get(j);
                highest = Math.max(highest, candle.highPrice());
                lowest = Math.min(lowest, candle.lowPrice());
            }
            
            result[i] = (highest + lowest) / 2.0;
        }
        
        return result;
    }
    
    private double[] calculateSenkouSpanA(double[] tenkan, double[] kijun) {
        double[] spanA = new double[tenkan.length];
        
        for (int i = 0; i < tenkan.length; i++) {
            if (tenkan[i] > 0 && kijun[i] > 0) {
                spanA[i] = (tenkan[i] + kijun[i]) / 2.0;
            }
        }
        
        return spanA;
    }
    
    private double[] calculateSenkouSpanB(List<CandleData> candles) {
        return calculateHighLowMiddle(candles, leadingSpanPeriod);
    }
    
    private double[] calculateChikouSpan(List<CandleData> candles) {
        double[] chikou = new double[candles.size()];
        
        for (int i = 0; i < candles.size() - 26; i++) {
            chikou[i + 26] = candles.get(i).closePrice();
        }
        
        return chikou;
    }
}
