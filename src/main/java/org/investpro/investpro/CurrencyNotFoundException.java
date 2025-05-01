package org.investpro.investpro;

import org.investpro.investpro.CurrencyType;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CurrencyNotFoundException extends Throwable {
    @Serial
    private static final long serialVersionUID = -5029723281334525952L;

    public CurrencyNotFoundException(@NotNull CurrencyType type, String symbol) {

        super(String.format("Currency '%s' with symbol '%s' not found.", type, symbol));
    }
}
