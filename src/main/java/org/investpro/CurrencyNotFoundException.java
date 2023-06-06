package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;

public class CurrencyNotFoundException extends Throwable {
    @Serial
    private static final long serialVersionUID = -5029723281334525952L;

    public CurrencyNotFoundException(@NotNull CurrencyType type, String symbol) {
        super(type.name().toLowerCase() + " currency not found for symbol: " + symbol);
    }
}
