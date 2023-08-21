package org.investpro;

public class CurrencyNotFoundException  extends RuntimeException {
    public CurrencyNotFoundException(CurrencyType currencyType, String message) {
        super(message);
    }



}
