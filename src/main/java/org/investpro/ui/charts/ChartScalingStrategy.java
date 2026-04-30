package org.investpro.ui.charts;

/**
 * Provides intelligent chart scaling strategies to ensure candlestick charts
 * display an optimal number of visible candles for readability.
 *
 * Targets 40-100 visible candles, with 60 preferred.
 */
public final class ChartScalingStrategy {

    public static final int MIN_VISIBLE_CANDLES = 40;
    public static final int MAX_VISIBLE_CANDLES = 100;
    public static final int TARGET_VISIBLE_CANDLES = 60;

    public static final int MIN_CANDLE_BODY_WIDTH = 2;
    public static final int MAX_CANDLE_BODY_WIDTH = 30;
    public static final int DEFAULT_CANDLE_BODY_WIDTH = 10;

    /**
     * Gap between candle bodies, in pixels.
     */
    public static final int CANDLE_SPACING = 1;


    private ChartScalingStrategy() {
        // Utility class
    }

    /**
     * Full horizontal slot occupied by one candle.
     */
    public static int candleSlotWidth(int candleBodyWidth) {
        return Math.max(1, candleBodyWidth) + CANDLE_SPACING;
    }

    /**
     * Visible candle count based on candle body width + spacing.
     */
    public static int calculateVisibleCandles(double chartWidthPixels, int candleBodyWidthPixels) {
        if (chartWidthPixels <= 0 || candleBodyWidthPixels <= 0) {
            return TARGET_VISIBLE_CANDLES;
        }

        int slotWidth = candleSlotWidth(candleBodyWidthPixels);
        return Math.max(1, (int) Math.floor(chartWidthPixels / slotWidth));
    }

    /**
     * Calculates body width needed to hit an ideal visible candle count.
     */
    public static int calculateOptimalCandleWidth(double chartWidthPixels, int totalCandles) {
        if (chartWidthPixels <= 0 || totalCandles <= 0) {
            return DEFAULT_CANDLE_BODY_WIDTH;
        }

        int targetCandles = Math.min(totalCandles, TARGET_VISIBLE_CANDLES);


        double rawSlotWidth = chartWidthPixels / targetCandles;
        int bodyWidth = (int) Math.floor(rawSlotWidth - CANDLE_SPACING);

        return clampCandleBodyWidth(bodyWidth);
    }

    public static int clampCandleBodyWidth(int candleBodyWidth) {
        return Math.max(
                MIN_CANDLE_BODY_WIDTH,
                Math.min(MAX_CANDLE_BODY_WIDTH, candleBodyWidth)
        );
    }

    public static boolean shouldAutoZoom(int visibleCandleCount) {
        return visibleCandleCount < MIN_VISIBLE_CANDLES;
    }

    public static boolean shouldZoomOut(int visibleCandleCount) {
        return visibleCandleCount > MAX_VISIBLE_CANDLES;
    }

    public static int suggestNextZoomLevel(int currentCandleBodyWidth, int visibleCandleCount) {
        if (visibleCandleCount < MIN_VISIBLE_CANDLES) {
            return clampCandleBodyWidth(currentCandleBodyWidth - 2);
        }

        if (visibleCandleCount > MAX_VISIBLE_CANDLES) {
            return clampCandleBodyWidth(currentCandleBodyWidth + 2);
        }

        return clampCandleBodyWidth(currentCandleBodyWidth);
    }

    public static boolean needsAdaptiveScaling(int availableCandleCount, int visibleCandleCount) {
        if (availableCandleCount <= 0) {
            return false;
        }

        return visibleCandleCount > MAX_VISIBLE_CANDLES
                || visibleCandleCount < MIN_VISIBLE_CANDLES
                || visibleCandleCount > availableCandleCount;
    }

    public static double calculateAspectRatio(double chartWidthPixels, double chartHeightPixels) {
        if (chartHeightPixels <= 0) {
            return 1.5;
        }

        return chartWidthPixels / chartHeightPixels;
    }

    public static int calculateChartPadding(double chartWidthPixels) {
        if (chartWidthPixels < 500) {
            return 5;
        }

        if (chartWidthPixels < 1000) {
            return 10;
        }

        return 15;
    }
}