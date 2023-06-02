package org.investpro.Indicators;

public enum Signal {
    UP,
    DOWN,

    BUY,
    SELL, STOP, CloseBUY, CloseSELL, ReduceSize;

    public static Signal getSignal() {
        return Signal.valueOf(Signal.class.getSimpleName());

    }
}
