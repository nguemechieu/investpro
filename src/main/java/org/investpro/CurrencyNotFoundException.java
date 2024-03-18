package org.investpro;

import java.io.Serial;


public class CurrencyNotFoundException extends Throwable {
    @Serial
    private static final long serialVersionUID = -5029723281334525952L;

    public CurrencyNotFoundException(CurrencyType type, String symbol) {
        super(STR."\{type.name().toLowerCase()} currency not found for symbol: \{symbol}");
    }
}
