package org.investpro;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Objects;

import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author NOEL NGUEMECHIEU
 */
public class MoneyAxisFormatter extends StringConverter<Number> {
    private final FastMoneyFormatter format;
    private final Currency currency;
    private final int precision;
    private final Logger logger = LoggerFactory.getLogger(MoneyAxisFormatter.class);

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
            try {
                return format.format(FastMoney.ofFiat(number.doubleValue(), currency.getCode(), precision));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                return format.format(FastMoney.ofCrypto(number.doubleValue(), currency.getCode(), precision));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Number fromString(String string) {
        try {
            // Create a number format instance for parsing the string
            NumberFormat format = NumberFormat.getInstance(Locale.getDefault());

            // Parse the string into a Number
            return format.parse(string);

        } catch (ParseException e) {
            // Handle parsing error, e.g., return null or throw an exception
            logger.error("Failed to parse string into a number: {}", string, e);
            return null; // or throw new IllegalArgumentException("Invalid number format");
        }
    }
}
