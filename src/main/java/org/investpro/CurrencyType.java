package org.investpro;

import lombok.Getter;

/**
 * @author NOEL NGUEMECHIEU
 */
@Getter
public enum CurrencyType {
    FIAT(0,"FIAT"),
    CRYPTO(1,"CRYPTO"),
    UNKNOWN(2,"UNKNOWN");

    private final int i;
    private final String unknown;

    CurrencyType(int i, String unknown) {
        this.i = i;
        this.unknown = unknown;
    }
}


