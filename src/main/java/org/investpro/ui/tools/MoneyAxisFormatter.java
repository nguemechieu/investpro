package org.investpro.ui.tools;

import javafx.util.StringConverter;
import lombok.Getter;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.DefaultMoney;
import org.investpro.models.currency.FastMoneyFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Formats numeric chart-axis values as money values for JavaFX charts.
 *
 * <p>Designed for InvestPro price axes across Forex, Crypto, Stocks,
 * Futures, Indices, Metals, and other quoted instruments.</p>
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
public final class MoneyAxisFormatter extends StringConverter<Number> {

    private static final int MIN_PRECISION = 0;
    private static final int MAX_PRECISION = 12;

    private final FastMoneyFormatter formatter;
    private final Currency currency;
    private final int precision;

    public MoneyAxisFormatter(Currency currency) {
        this(currency, currency == null ? 2 : currency.getFractionalDigits());
    }

    public MoneyAxisFormatter(Currency currency, int precision) {
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.precision = clampPrecision(precision);
        this.formatter = new FastMoneyFormatter();
    }

    @Override
    public String toString(Number number) {
        if (number == null) {
            return "";
        }
        double rawValue = number.doubleValue();
        if (!Double.isFinite(rawValue)) {
            return "";
        }

        int effectivePrecision = precisionForValue(rawValue);
        BigDecimal value = toBigDecimal(number)
                .setScale(effectivePrecision, RoundingMode.HALF_UP);

        return formatter.format(DefaultMoney.of(value,currency));
    }

    @Override
    public Number fromString(String string) {
        if (string == null || string.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return formatter.parse(string.trim()).getNumber();
        } catch (Exception ignored) {
            return parseFallback(string);
        }
    }

    private static int clampPrecision(int precision) {
        return Math.max(MIN_PRECISION, Math.min(MAX_PRECISION, precision));
    }

    private int precisionForValue(double value) {
        double abs = Math.abs(value);
        if (abs == 0.0 || abs >= 1.0) {
            return precision;
        }

        int leadingZeroPrecision = (int) Math.ceil(-Math.log10(abs)) + 2;
        return clampPrecision(Math.max(precision, leadingZeroPrecision));
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        if (number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long) {
            return BigDecimal.valueOf(number.longValue());
        }

        return BigDecimal.valueOf(number.doubleValue());
    }

    private static Number parseFallback(String text) {
        String cleaned = text
                .trim()
                .replace(",", "")
                .replaceAll("[^0-9.\\-]", "");

        if (cleaned.isBlank() || "-".equals(cleaned) || ".".equals(cleaned)) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }
}
