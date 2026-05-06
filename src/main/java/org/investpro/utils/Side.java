package org.investpro.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum Side {
    BUY,
    SELL,HOLD;


    public static Side getSide(@NotNull String type) {
        if (type.equalsIgnoreCase("BUY")) {
            return BUY;
        } else if (type.equalsIgnoreCase("SELL")) {
            return SELL;
        } else {
            throw new IllegalArgumentException("unknown trade type: %s".formatted(type));
        }
    }

    @Contract(pure = true)
    public static Side oppositeOf(@NotNull Side side) {
        return switch (side) {
            case BUY -> SELL;
            case SELL -> BUY;
            default -> throw new IllegalArgumentException("unknown side: %s".formatted(side));
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.US);
    }
}
