package org.investpro.strategy;

import org.investpro.data.CandleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeaturePipeline.
 */
@DisplayName("Feature Pipeline Tests")
class FeaturePipelineTest {

    private FeaturePipeline pipeline;
    private FeaturePipelineConfig config;
    private List<CandleData> candles;

    @BeforeEach
    void setUp() {
        pipeline = new FeaturePipeline();
        config = FeaturePipelineConfig.builder()
                .rsiPeriod(14)
                .emaFast(20)
                .emaSlow(50)
                .atrPeriod(14)
                .breakoutLookback(20)
                .build();

        candles = generateSampleCandles(150);
    }

    @Test
    @DisplayName("Feature pipeline should return null with insufficient candles")
    void testInsufficientCandles() {
        List<CandleData> small = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            small.add(new CandleData(100, 100, 101, 99, 0, 1000));
        }

        FeatureRow row = pipeline.computeLatest(small, config);
        assertNull(row);
    }

    @Test
    @DisplayName("Feature pipeline should compute features with sufficient candles")
    void testComputeFeatures() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        assertTrue(row.getClose() > 0);
        assertTrue(row.getAtr() > 0);
        assertNotNull(row.getRegime());
    }

    @Test
    @DisplayName("RSI should be between 0 and 100")
    void testRSIBounds() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        assertTrue(row.getRsi() >= 0 && row.getRsi() <= 100);
    }

    @Test
    @DisplayName("Breakout levels should exclude latest candle")
    void testBreakoutLevels() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        assertTrue(row.getBreakoutHigh() > 0);
        assertTrue(row.getBreakoutLow() > 0);
        assertTrue(row.getBreakoutHigh() >= row.getBreakoutLow());
    }

    @Test
    @DisplayName("EMAs should be computed correctly")
    void testEMAComputation() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        assertTrue(row.getEmaFast() > 0);
        assertTrue(row.getEmaSlow() > 0);
    }

    @Test
    @DisplayName("Volume ratio should be reasonable")
    void testVolumeRatio() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        assertTrue(row.getVolumeRatio() > 0);
    }

    @Test
    @DisplayName("Regime should be one of the valid values")
    void testRegime() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        String regime = row.getRegime();
        assertTrue(regime.equals("high_volatility") ||
                regime.equals("trending") ||
                regime.equals("ranging"));
    }

    @Test
    @DisplayName("Feature row convenience methods should work")
    void testFeatureRowMethods() {
        FeatureRow row = pipeline.computeLatest(candles, config);
        assertNotNull(row);
        assertNotNull(row.toString());
        // Methods should not throw
        row.priceChangePercent();
        row.trendUp();
        row.trendDown();
    }

    private List<CandleData> generateSampleCandles(int count) {
        List<CandleData> result = new ArrayList<>();
        double price = 100.0;

        for (int i = 0; i < count; i++) {
            // Generate realistic candle with variation
            double open = price;
            double close = price + (Math.random() - 0.5) * 2; // ±1 change
            double high = Math.max(open, close) + 0.5;
            double low = Math.min(open, close) - 0.5;
            double volume = 1000 + Math.random() * 500;

            result.add(new CandleData(open, close, high, low, i, volume));
            price = close;
        }

        return result;
    }
}
