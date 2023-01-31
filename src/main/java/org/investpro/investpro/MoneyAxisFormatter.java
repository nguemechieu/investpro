package org.investpro.investpro;

import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MoneyAxisFormatter extends StringConverter<Number> {
    private final FastMoneyFormatter format;
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
        if (currency.getCurrencyType() == CurrencyType.FIAT) {
            return format.format(FastMoney.ofFiat(number.doubleValue(), currency.getCode(), precision));
        } else {
            return format.format(FastMoney.ofCrypto(number.doubleValue(), currency.getCode(), precision));
        }
    }

    @Override
    public Number fromString(@NotNull String string) {

        return string.length()
                ;

    }
}
