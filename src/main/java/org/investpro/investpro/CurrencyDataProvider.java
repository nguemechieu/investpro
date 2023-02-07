package org.investpro.investpro;


import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class CurrencyDataProvider {
    protected abstract CompletableFuture<List<Currency>> registerCurrencies() throws URISyntaxException, IOException;
    // protected abstract void registerCurrencies() throws Exception;
}
