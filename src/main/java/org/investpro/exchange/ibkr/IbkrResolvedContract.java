package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.Objects;

public record IbkrResolvedContract(
        long conId,
        String symbol,
        String localSymbol,
        String secType,
        String currency,
        String exchange,
        String primaryExchange,
        String tradingClass,
        String multiplier,
        String lastTradeDateOrContractMonth,
        Double strike,
        String right,
        Long underlyingConId,
        Double minTick,
        String marketRuleIds,
        String longName,
        String category,
        String subcategory,
        String discoveredFrom,
        Instant lastSeenAt,
        Instant lastRefreshedAt,
        String metadataJson) {

    public IbkrResolvedContract {
        if (conId <= 0) {
            throw new IllegalArgumentException("IBKR conId must be positive.");
        }
        symbol = safe(symbol);
        localSymbol = safe(localSymbol);
        secType = safe(secType);
        currency = safe(currency);
        exchange = safe(exchange);
        primaryExchange = safe(primaryExchange);
        tradingClass = safe(tradingClass);
        multiplier = safe(multiplier);
        lastTradeDateOrContractMonth = safe(lastTradeDateOrContractMonth);
        right = safe(right);
        marketRuleIds = safe(marketRuleIds);
        longName = safe(longName);
        category = safe(category);
        subcategory = safe(subcategory);
        discoveredFrom = safe(discoveredFrom);
        lastSeenAt = lastSeenAt == null ? Instant.now() : lastSeenAt;
        lastRefreshedAt = lastRefreshedAt == null ? lastSeenAt : lastRefreshedAt;
        metadataJson = safe(metadataJson);
    }

    public String uniqueKey() {
        return conId + "|" + exchange.toUpperCase();
    }

    public String userFriendlySymbol() {
        if ("CASH".equalsIgnoreCase(secType) && symbol.length() == 3 && currency.length() == 3) {
            return symbol + "/" + currency;
        }
        if ("FUT".equalsIgnoreCase(secType) && !lastTradeDateOrContractMonth.isBlank()) {
            return "%s %s".formatted(symbol, formatFutureMonth(lastTradeDateOrContractMonth));
        }
        return localSymbol.isBlank() ? symbol : localSymbol;
    }

    private static String formatFutureMonth(String value) {
        if (value.length() < 6) {
            return value;
        }
        String month = switch (value.substring(4, 6)) {
            case "01" -> "JAN";
            case "02" -> "FEB";
            case "03" -> "MAR";
            case "04" -> "APR";
            case "05" -> "MAY";
            case "06" -> "JUN";
            case "07" -> "JUL";
            case "08" -> "AUG";
            case "09" -> "SEP";
            case "10" -> "OCT";
            case "11" -> "NOV";
            case "12" -> "DEC";
            default -> value.substring(4, 6);
        };
        return month + " " + value.substring(0, 4);
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
