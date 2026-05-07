package org.investpro.execution;

import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionTransitionPolicyTest {

    private final PositionTransitionPolicy policy = new PositionTransitionPolicy();

    @Test
    void noPositionBuyOpensLong() {
        assertEquals(ExecutionIntent.OPEN_LONG, policy.resolveIntent(null, Side.BUY));
    }

    @Test
    void noPositionSellOpensShort() {
        assertEquals(ExecutionIntent.OPEN_SHORT, policy.resolveIntent(null, Side.SELL));
    }

    @Test
    void buyPositionBuySignalDoesNothing() {
        assertEquals(ExecutionIntent.NO_ACTION, policy.resolveIntent(Side.BUY, Side.BUY));
    }

    @Test
    void sellPositionSellSignalDoesNothing() {
        assertEquals(ExecutionIntent.NO_ACTION, policy.resolveIntent(Side.SELL, Side.SELL));
    }

    @Test
    void buyPositionSellSignalClosesLongOnly() {
        assertEquals(ExecutionIntent.CLOSE_LONG_ONLY, policy.resolveIntent(Side.BUY, Side.SELL));
    }

    @Test
    void sellPositionBuySignalClosesShortOnly() {
        assertEquals(ExecutionIntent.CLOSE_SHORT_ONLY, policy.resolveIntent(Side.SELL, Side.BUY));
    }

    @Test
    void holdSignalDoesNothing() {
        assertEquals(ExecutionIntent.NO_ACTION, policy.resolveIntent(Side.BUY, Side.HOLD));
        assertEquals(ExecutionIntent.NO_ACTION, policy.resolveIntent(Side.SELL, Side.HOLD));
        assertEquals(ExecutionIntent.NO_ACTION, policy.resolveIntent(null, Side.HOLD));
    }
}
