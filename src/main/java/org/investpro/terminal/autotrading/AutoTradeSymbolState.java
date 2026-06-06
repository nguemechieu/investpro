package org.investpro.terminal.autotrading;

public enum AutoTradeSymbolState {
    DISCOVERED,
    ELIGIBILITY_CHECKING,
    NOT_TRADEABLE,
    ELIGIBLE,
    WAITING_FOR_DATA,
    STRATEGY_ASSIGNED,
    WATCHING,
    SIGNAL_FOUND,
    RISK_REJECTED,
    ORDER_PLANNED,
    ORDER_SUBMITTED,
    IN_POSITION,
    PAUSED,
    ERROR,
    DISABLED_BY_USER
}
