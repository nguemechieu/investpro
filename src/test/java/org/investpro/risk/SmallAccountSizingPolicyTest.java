package org.investpro.risk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallAccountSizingPolicyTest {

    @Test
    void oandaBalance50ForcesOneUnit() {
        assertEquals(1.0, SmallAccountSizingPolicy.apply("OANDA", 50.0, null, null, 1000.0));
    }

    @Test
    void oandaBalanceBelowThresholdForcesOneUnit() {
        assertEquals(1.0, SmallAccountSizingPolicy.apply("OANDA", 99.99, null, null, 1000.0));
    }

    @Test
    void oandaBalanceAtThresholdKeepsRequestedUnits() {
        assertEquals(1000.0, SmallAccountSizingPolicy.apply("OANDA", 100.0, null, null, 1000.0));
    }

    @Test
    void coinbaseSmallBalanceKeepsRequestedUnits() {
        assertEquals(1000.0, SmallAccountSizingPolicy.apply("Coinbase", 50.0, null, null, 1000.0));
    }

    @Test
    void negativeRequestedUnitsBecomeZero() {
        assertEquals(0.0, SmallAccountSizingPolicy.apply("Coinbase", 50.0, null, null, -25.0));
    }
}
