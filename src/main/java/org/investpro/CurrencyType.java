package org.investpro;

public enum CurrencyType {
    FIAT,
    CRYPTO,
    NULL;

    public short getIsoCode() {
        return (short) ordinal();
    }
}
