package org.investpro.ui.charts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CandleStickChartClampTest {
    private static double clamp(double value, double min, double max) throws Exception {
        var method = CandleStickChart.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(null, value, min, max);
    }

    @Test void crosshairNearLeftEdgeClampsToLabelMargin() throws Exception {
        assertEquals(2.0, clamp(-51.0, 2.0, 648.0));
    }

    @Test void undersizedCanvasKeepsLowerMarginWhenLabelCannotFit() throws Exception {
        assertEquals(2.0, clamp(25.0, 2.0, -51.0));
        assertEquals(32.0, clamp(10.0, 32.0, -5.0));
    }

    @Test void ordinaryValuesStayWithinBounds() throws Exception {
        assertEquals(25.0, clamp(25.0, 2.0, 100.0));
        assertEquals(100.0, clamp(150.0, 2.0, 100.0));
        assertEquals(2.0, clamp(2.0, 2.0, 100.0));
        assertEquals(2.0, clamp(25.0, 2.0, 2.0));
    }

    @Test void floatingPointEdgeCasesKeepMinMaxSemantics() throws Exception {
        assertTrue(Double.isNaN(clamp(Double.NaN, 0.0, 1.0)));
        assertEquals(1.0, clamp(Double.POSITIVE_INFINITY, 0.0, 1.0));
        assertEquals(0.0, clamp(Double.NEGATIVE_INFINITY, 0.0, 1.0));
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(clamp(-0.0, 0.0, 1.0)));
    }
}
