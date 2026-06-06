package org.investpro.exchange.ibkr;

import java.util.Objects;

public record IbkrContractCandidate(
        Long conId,
        String symbol,
        String description,
        IbkrSecurityType securityType,
        String secType,
        String exchange,
        String primaryExchange,
        String currency,
        String localSymbol,
        String tradingClass,
        String lastTradeDateOrContractMonth,
        String multiplier,
        String derivativeSecTypes,
        String source,
        String metadataJson) {

    public IbkrContractCandidate {
        symbol = safe(symbol);
        description = safe(description);
        securityType = securityType == null ? IbkrSecurityType.UNKNOWN : securityType;
        secType = safe(secType);
        exchange = safe(exchange);
        primaryExchange = safe(primaryExchange);
        currency = safe(currency);
        localSymbol = safe(localSymbol);
        tradingClass = safe(tradingClass);
        lastTradeDateOrContractMonth = safe(lastTradeDateOrContractMonth);
        multiplier = safe(multiplier);
        derivativeSecTypes = safe(derivativeSecTypes);
        source = safe(source);
        metadataJson = safe(metadataJson);
    }

    public String displayLabel() {
        String name = !description.isBlank() ? description : symbol;
        String venue = !primaryExchange.isBlank() ? primaryExchange : exchange;
        String type = !secType.isBlank() ? secType : securityType.ibkrCode();
        String expiry = lastTradeDateOrContractMonth.isBlank() ? "" : " " + lastTradeDateOrContractMonth;
        return "%s %s%s %s %s".formatted(symbol, expiry, venue.isBlank() ? "" : " @ " + venue, type, currency)
                .replaceAll("\\s+", " ")
                .trim()
                + (name.equals(symbol) ? "" : " - " + name);
    }

    public boolean hasConId() {
        return conId != null && conId > 0;
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
