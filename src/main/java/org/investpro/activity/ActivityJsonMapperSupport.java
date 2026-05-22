package org.investpro.activity;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class ActivityJsonMapperSupport {
    private ActivityJsonMapperSupport() {
    }

    public static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    public static BigDecimal decimal(JsonNode node, String... fields) {
        String text = text(node, fields);
        if (text == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    public static Instant instant(JsonNode node, String... fields) {
        String text = text(node, fields);
        if (text == null) {
            return Instant.now();
        }
        try {
            if (text.matches("\\d+")) {
                long value = Long.parseLong(text);
                return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
            }
            return Instant.parse(text);
        } catch (DateTimeParseException | NumberFormatException exception) {
            return Instant.now();
        }
    }

    public static TradePair pair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        try {
            return TradePair.fromSymbol(symbol);
        } catch (SQLException | ClassNotFoundException | RuntimeException exception) {
            return null;
        }
    }

    public static Side side(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BUY", "B", "LONG" -> Side.BUY;
            case "SELL", "S", "SHORT" -> Side.SELL;
            default -> null;
        };
    }
}
