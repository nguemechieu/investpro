package org.investpro.investpro;

import java.util.Locale;

public enum TradingMode {
    PAPER("Paper"),
    LIVE("Live");

    private final String label;

    TradingMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String badgeLabel() {
        return name();
    }

    public boolean isPaper() {
        return this == PAPER;
    }

    public boolean isLive() {
        return this == LIVE;
    }

    public static TradingMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return PAPER;
        }

        String normalized = rawValue.trim().toUpperCase(Locale.US);
        return "LIVE".equals(normalized) ? LIVE : PAPER;
    }

    @Override
    public String toString() {
        return label;
    }
}
