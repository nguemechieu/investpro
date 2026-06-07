package org.investpro.agent.symbol;

public enum SymbolAgentStatus {
    CREATED,
    STARTING,
    WARMING_UP,
    ACTIVE,
    PAUSED,
    WAITING_FOR_DATA,
    WAITING_FOR_TRADABILITY,
    RISK_BLOCKED,
    STRATEGY_UNASSIGNED,
    ERROR,
    STOPPING,
    STOPPED
}
