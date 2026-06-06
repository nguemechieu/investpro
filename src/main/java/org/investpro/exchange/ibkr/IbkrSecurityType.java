package org.investpro.exchange.ibkr;

import java.util.Locale;

public enum IbkrSecurityType {
    STOCK("STK"),
    ETF("STK"),
    FOREX("CASH"),
    FUTURE("FUT"),
    OPTION("OPT"),
    INDEX("IND"),
    CFD("CFD"),
    UNKNOWN("");

    private final String ibkrCode;

    IbkrSecurityType(String ibkrCode) {
        this.ibkrCode = ibkrCode;
    }

    public String ibkrCode() {
        return ibkrCode;
    }

    public static IbkrSecurityType fromIbkrCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STK" -> STOCK;
            case "CASH" -> FOREX;
            case "FUT", "CONTFUT" -> FUTURE;
            case "OPT", "FOP", "WAR" -> OPTION;
            case "IND" -> INDEX;
            case "CFD" -> CFD;
            default -> UNKNOWN;
        };
    }
}
