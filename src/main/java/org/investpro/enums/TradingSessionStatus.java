package org.investpro.enums;

import lombok.Getter;

@Getter
public enum TradingSessionStatus {
    OPEN("Open", true),
    CLOSED("Closed", false),
    BREAK("Session Break", false),
    UNKNOWN("Unknown", false);

    private final String displayName;
    private final boolean tradable;

    TradingSessionStatus(String displayName, boolean tradable) {
        this.displayName = displayName;
        this.tradable = tradable;
    }
}