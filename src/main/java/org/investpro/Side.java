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
            throw new IllegalArgumentException(STR."unknown trade type: \{type}");
        }
    }

    public static Side oppositeOf(Side side) {
        switch (side) {
            case BUY:
                return SELL;
            case SELL:
                return BUY;
            default:
                throw new IllegalArgumentException("unknown side: " + side);
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.US);
    }
}
