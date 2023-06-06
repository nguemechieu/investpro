package org.investpro;


public class Candle extends CandleData {


    public Candle(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime, double volume) {
        super(openPrice, closePrice, highPrice, lowPrice, openTime, volume);
    }

    public Candle(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime, double volume, double averagePrice, double volumeWeightedAveragePrice, boolean placeHolder) {
        super(openPrice, closePrice, highPrice, lowPrice, openTime, volume, averagePrice, volumeWeightedAveragePrice, placeHolder);
    }
}
