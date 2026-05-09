package org.investpro.core.execution;

import org.investpro.utils.Side;

public final class PositionTransitionPolicy {

    public ExecutionIntent resolveIntent(Side currentPositionSide, Side signalSide) {
        if (signalSide == null || signalSide == Side.HOLD) {
            return ExecutionIntent.NO_ACTION;
        }

        if (currentPositionSide == null || currentPositionSide == Side.HOLD) {
            return signalSide == Side.BUY ? ExecutionIntent.OPEN_LONG : ExecutionIntent.OPEN_SHORT;
        }

        if (currentPositionSide == signalSide) {
            return ExecutionIntent.NO_ACTION;
        }

        if (currentPositionSide == Side.BUY && signalSide == Side.SELL) {
            return ExecutionIntent.CLOSE_LONG_ONLY;
        }

        if (currentPositionSide == Side.SELL && signalSide == Side.BUY) {
            return ExecutionIntent.CLOSE_SHORT_ONLY;
        }

        return ExecutionIntent.NO_ACTION;
    }
}
