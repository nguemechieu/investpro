package org.investpro.ui.charts;

import org.investpro.enums.timeframe.Timeframe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChartTimeframeTest {
    @Test
    void chartNodesKeepIdentityEqualityForJavaFxChildTracking() throws Exception {
        // Parent tracks children in a set: mutable chart data must never affect identity.
        for (Class<?> nodeType : new Class<?>[] {ChartContainer.class, CandleStickChart.class}) {
            assertEquals(Object.class, nodeType.getMethod("equals", Object.class).getDeclaringClass());
            assertEquals(Object.class, nodeType.getMethod("hashCode").getDeclaringClass());
        }
    }

    @Test
    void minuteAndMonthCodesRemainDistinct() {
        assertEquals(Timeframe.M1, Timeframe.fromCode("1m"));
        assertEquals(Timeframe.MN, Timeframe.fromCode("1M"));
    }

    @Test
    void eightHourDurationIsCorrect() {
        assertEquals(8 * 3600, Timeframe.H8.getSeconds());
        assertEquals(Timeframe.H8, Timeframe.fromSeconds(8 * 3600));
    }
}
