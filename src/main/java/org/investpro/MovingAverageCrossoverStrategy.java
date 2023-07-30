package org.investpro;

import org.apache.commons.math3.stat.descriptive.moment.Mean;

public class MovingAverageCrossoverStrategy {

    private double[] priceData;
    private int shortPeriod;
    private int longPeriod;

    public MovingAverageCrossoverStrategy(double[] priceData, int shortPeriod, int longPeriod) {
        this.priceData = priceData;
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    public int[] generateSignals() {
        int[] signals = new int[priceData.length];

        // Calculate short and long moving averages
        double[] shortMA = calculateMovingAverage(shortPeriod);
        double[] longMA = calculateMovingAverage(longPeriod);

        // Generate signals based on crossover
        for (int i = 1; i < priceData.length; i++) {
            if (shortMA[i] > longMA[i] && shortMA[i - 1] <= longMA[i - 1]) {
                signals[i] = 1; // Buy signal
            } else if (shortMA[i] < longMA[i] && shortMA[i - 1] >= longMA[i - 1]) {
                signals[i] = -1; // Sell signal
            } else {
                signals[i] = 0; // No signal
            }
        }

        return signals;
    }

    private double[] calculateMovingAverage(int period) {
        Mean mean = new Mean();
        double[] movingAverage = new double[priceData.length];

        for (int i = 0; i < priceData.length; i++) {
            int startIndex = Math.max(0, i - period + 1);
            double[] subset = new double[i - startIndex + 1];
            System.arraycopy(priceData, startIndex, subset, 0, i - startIndex + 1);
            movingAverage[i] = mean.evaluate(subset);
        }

        return movingAverage;
    }

    public static void main(String[] args) {
        double[] priceData = {100, 102, 105, 98, 110, 112, 115, 120, 118, 122}; // Replace with actual historical price data
        int shortPeriod = 3;
        int longPeriod = 5;

        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(priceData, shortPeriod, longPeriod);
        int[] signals = strategy.generateSignals();

        // Display the generated signals
        for (int i = 0; i < signals.length; i++) {
            System.out.println("Day " + (i + 1) + ": Signal -> " + signals[i]);
        }
    }
}
