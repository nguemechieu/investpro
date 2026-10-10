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
    FUTURES_OPTION("FOP"),
    CONTINUOUS_FUTURE("CONTFUT"),
    BOND("BOND"),
    FUND("FUND"),
    WARRANT("WAR"),
    COMMODITY("CMDTY"),
    CRYPTO("CRYPTO"),
    COMBINATION("BAG"),
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
            case "FUT" -> FUTURE;
            case "CONTFUT" -> CONTINUOUS_FUTURE;
            case "OPT" -> OPTION;
            case "FOP" -> FUTURES_OPTION;
            case "WAR", "IOPT" -> WARRANT;
            case "BOND" -> BOND;
            case "FUND" -> FUND;
            case "CMDTY" -> COMMODITY;
            case "CRYPTO" -> CRYPTO;
            case "BAG" -> COMBINATION;
            case "IND" -> INDEX;
            case "CFD" -> CFD;
            default -> UNKNOWN;
        };
    }
}
