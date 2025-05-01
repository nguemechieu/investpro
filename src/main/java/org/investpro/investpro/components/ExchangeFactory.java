package org.investpro.investpro.components;

import org.investpro.investpro.Exchange;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExchangeFactory {

    private final Map<String, Exchange> exchanges = new HashMap<>(

    );

    public ExchangeFactory(@NotNull List<Exchange> exchangeList) {
        for (Exchange ex : exchangeList) {
            String name = ex.getClass().getSimpleName().replace("Exchange", "").toLowerCase();
            exchanges.put(name, ex);
        }
    }

    public Exchange getExchange(String name) {
        return Optional.ofNullable(exchanges.get(name.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + name));
    }
}
