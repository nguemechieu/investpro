package org.investpro.backtesting.simulation;

/**
 * Incremental mean/variance calculator used to avoid temporary return arrays.
 */
final class WelfordStatistics {
    private long count;
    private double mean;
    private double m2;

    void add(double value) {
        count++;
        double delta = value - mean;
        mean += delta / count;
        double delta2 = value - mean;
        m2 += delta * delta2;
    }

    long count() {
        return count;
    }

    double mean() {
        return mean;
    }

    double variance() {
        return count > 1 ? m2 / (count - 1) : 0.0;
    }

    double standardDeviation() {
        return Math.sqrt(Math.max(0.0, variance()));
    }

    void reset() {
        count = 0L;
        mean = 0.0;
        m2 = 0.0;
    }
}
