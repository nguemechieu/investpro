package org.investpro.models.currency;

import java.util.stream.IntStream;

/**
 * High-performance Money formatter that handles both DefaultMoney and FastMoney instances.
 * Uses efficient character buffer operations to minimize object allocation.
 * 
 * @author Michael Ennen
 */
public class FastMoneyFormatter implements MoneyFormatter<Money> {
    @Override
    public String format(Money money) {
        if (money instanceof DefaultMoney) {
            DefaultMoneyFormatter defaultMoneyFormatter;
            if (money.currency().getCurrencyType() == CurrencyType.FIAT) {
                defaultMoneyFormatter = DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER;
            } else {
                defaultMoneyFormatter = DefaultMoneyFormatter.DEFAULT_CRYPTO_FORMATTER;
            }

            return defaultMoneyFormatter.format((DefaultMoney) money);
        } else if (money instanceof FastMoney) {
            return format((FastMoney) money);
        } else {
            throw new IllegalArgumentException("Unknown money type: %s".formatted(money.getClass()));
        }
    }

    private String format(FastMoney money) {
        int currencyFractionalDigits = money.currency().getFractionalDigits();

        // Handle zero or edge case amounts
        if (money.getPrecision() == 0 || money.amount() == 0) {
            return formatZeroAmount(money, currencyFractionalDigits);
        }

        final char[] buf = new char[FastMoney.Utils.MAX_LONG_LENGTH + 3];
        int p = buf.length;
        int remainingPrecision = money.getPrecision();
        long units = Math.abs(money.amount());
        long q;
        long rem;

        // Convert amount to string representation
        while (remainingPrecision > 0 && units > 0) {
            q = units / 10;
            rem = (int) (units - q * 10);
            buf[--p] = (char) ('0' + rem);
            units = q;
            --remainingPrecision;
        }

        // Insert decimal point and pad with zeros as needed
        insertDecimalPoint(buf, p, units, remainingPrecision);

        // Add sign if negative
        if (money.amount() < 0) {
            buf[--p] = '-';
        }

        // Add currency symbol/code
        addCurrencyIndicator(buf, p, money.currency());

        return new String(buf, p, buf.length - p);
    }

    /**
     * Format amounts where precision is 0 or amount is 0.
     */
    private String formatZeroAmount(FastMoney money, int currencyFractionalDigits) {
        if (money.currency().getCurrencyType() == CurrencyType.FIAT) {
            StringBuilder sb = new StringBuilder();
            sb.append(money.currency().getSymbol());
            sb.append("0");
            if (currencyFractionalDigits > 0) {
                sb.append(".");
                IntStream.range(0, currencyFractionalDigits).mapToObj(i -> "0").forEachOrdered(sb::append);
            }
            return sb.toString();
        } else {
            return "0 " + money.currency().getCode();
        }
    }

    /**
     * Insert decimal point and pad with zeros for fractional digits.
     */
    private void insertDecimalPoint(char[] buf, int startPos, long units, int remainingPrecision) {
        if (units == 0 && remainingPrecision == 0) {
            // All digits accounted for - just add decimal point
            buf[--startPos] = '.';
            buf[--startPos] = '0';
        } else if (units == 0) {
            // Pad remaining zeros for fractional part
            while (remainingPrecision > 0) {
                buf[--startPos] = '0';
                --remainingPrecision;
            }
            buf[--startPos] = '.';
            buf[--startPos] = '0';
        } else if (remainingPrecision == 0) {
            // No fractional part remaining - add decimal point and integer digits
            buf[--startPos] = '.';
            while (units > 0) {
                long q = units / 10;
                long rem = (int) (units - q * 10);
                buf[--startPos] = (char) ('0' + rem);
                units = q;
            }
        }
    }

    /**
     * Add currency symbol (for fiat) or code (for crypto) to buffer.
     */
    private void addCurrencyIndicator(char[] buf, int startPos, Currency currency) {
        if (currency.getCurrencyType() == CurrencyType.FIAT) {
            String symbol = currency.getSymbol();
            if (!symbol.isEmpty()) {
                buf[startPos] = symbol.charAt(0);
            }
        }
    }
}
