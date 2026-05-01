package org.investpro.ui.tools;

import javafx.util.StringConverter;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.DefaultMoney;
import org.investpro.models.currency.FastMoneyFormatter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * @author NOEL NGUEMECHIEU
 */
public class MoneyAxisFormatter extends StringConverter<Number> {
    public final FastMoneyFormatter format;
    private final Currency currency;
    private final int precision;

    public MoneyAxisFormatter(Currency currency) {
        this(currency, currency.getFractionalDigits());
    }

    public MoneyAxisFormatter(Currency currency, int precision) {
        Objects.requireNonNull(currency, "currency must not be null");

        this.currency = currency;
        this.precision = precision;
        format = new FastMoneyFormatter();
    }

    @Override
    public String toString(Number number) {
        if (number == null) {
            return "";
        }

        BigDecimal value = BigDecimal.valueOf(number.doubleValue()).setScale(precision, java.math.RoundingMode.HALF_UP);
        return format.format(DefaultMoney.of(value, currency));
    }

    @Override
    public Number fromString(String string) {
        // return format.parse(string).getNumber();
        return 1;
    }
}
