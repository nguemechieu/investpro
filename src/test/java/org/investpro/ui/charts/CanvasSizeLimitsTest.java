package org.investpro.ui.charts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasSizeLimitsTest {
    @Test
    void enormousLayoutRequestsStayWithinTextureLimit() {
        assertEquals(4096, CanvasSizeLimits.limit(Double.MAX_VALUE, 800, 1));
        assertEquals(800, CanvasSizeLimits.limit(Double.NaN, 800, 1));
        assertEquals(800, CanvasSizeLimits.limit(Double.POSITIVE_INFINITY, 800, 1));
    }

    @Test
    void boundsDevicePixelsOnScaledDisplays() {
        for (double scale : new double[] {1, 1.25, 1.5, 2, 3}) {
            double size = CanvasSizeLimits.limit(10000, 800, scale);
            assertTrue(Math.ceil(size * scale) <= 4096);
        }
    }

    @Test
    void preservesOrdinarySizesAndHandlesInitialLayout() {
        assertEquals(800, CanvasSizeLimits.limit(800, 400, 2));
        assertEquals(400, CanvasSizeLimits.limit(0, 400, 1));
        assertEquals(400, CanvasSizeLimits.limit(-1, 400, Double.NaN));
    }
}
