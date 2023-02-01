package org.investpro.investpro;

import org.jetbrains.annotations.Contract;
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

    @Contract(pure = true)
    public static Side oppositeOf(@NotNull Side side) {
        return switch (side) {
            case BUY -> SELL;
            case SELL -> BUY;
            default -> throw new IllegalArgumentException("unknown side: " + side);
        };
    }

    @Override
    public @NotNull String toString() {
        return name().toLowerCase(Locale.US);
    }
}
