package org.investpro.models.market;

public enum TradingEnvironment {
    LIVE,
    PAPER,
    PRACTICE,
    SANDBOX,
    TESTNET,
    BACKTEST,
    SIMULATION,
    UNKNOWN;

    public boolean isRealMoney() {
        return this == LIVE;
    }

    public boolean isSimulated() {
        return this == PAPER
                || this == PRACTICE
                || this == SANDBOX
                || this == TESTNET
                || this == BACKTEST
                || this == SIMULATION;
    }
}
