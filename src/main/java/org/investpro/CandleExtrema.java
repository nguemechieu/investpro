package org.investpro;

/**
 * Helper class to store extrema values (highest and lowest prices) in visible candlesticks.
 */
record CandleExtrema(double high, double low, int highIndex, int lowIndex) {
}
