package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum Side {
    BUY,
    SELL;

    public static Side getSide(@NotNull String type) {
        if (type.equalsIgnoreCase("BUY")) {
            return BUY;
        } else if (type.equalsIgnoreCase("SELL")) {
            return SELL;
        } else {
            throw new IllegalArgumentException("unknown trade type: " + type);
        }
    }

    @Override
    public @NotNull String toString() {
        return name().toLowerCase(Locale.US);
    }
}
