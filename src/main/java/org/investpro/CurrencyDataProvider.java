package org.investpro;


import java.io.IOException;
import java.net.URISyntaxException;

public abstract class CurrencyDataProvider {
    protected abstract void registerCurrencies() throws URISyntaxException, IOException;
    // protected abstract void registerCurrencies() throws Exception;
}
