package org.investpro.exchange.execution;

/** How an order executes, independently of its exchange or product venue. */
public enum ExecutionMode {
    LOCAL_PAPER, LIVE, BACKTEST;

    public boolean isLocal() { return this != LIVE; }
}
