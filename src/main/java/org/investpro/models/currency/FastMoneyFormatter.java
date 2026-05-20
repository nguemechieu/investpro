package org.investpro.models.currency;

import javax.swing.SpinnerNumberModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * High-performance Money formatter that handles both DefaultMoney and FastMoney instances.
 *
 * <p>Formats fiat as symbol-prefixed values, for example: $1,234.56</p>
 * <p>Formats crypto as value plus currency code, for example: 0.00250000 BTC</p>
 *
 * @author Michael Ennen
 * @author NOEL NGUEMECHIEU
 */
public class FastMoneyFormatter implements MoneyFormatter<Money> {

    private static final int DEFAULT_PARSE_SCALE = 12;

    @Override
    public String format(Money money) {
        Objects.requireNonNull(money, "money must not be null");

        if (money instanceof DefaultMoney defaultMoney) {
            DefaultMoneyFormatter formatter =
                    money.currency().getCurrencyType() == CurrencyType.FIAT
                            ? DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER
                            : DefaultMoneyFormatter.DEFAULT_CRYPTO_FORMATTER;

            return formatter.format(defaultMoney);
        }

        if (money instanceof FastMoney fastMoney) {
            return formatFastMoney(fastMoney);
        }

        throw new IllegalArgumentException("Unknown money type: %s".formatted(money.getClass()));
    }

    private String formatFastMoney(FastMoney money) {
        Objects.requireNonNull(money, "money must not be null");

        Currency currency = Objects.requireNonNull(money.currency(), "currency must not be null");
        BigDecimal decimalAmount = toBigDecimal(money);

        int scale = scaleFor(currency, money.getPrecision());
        decimalAmount = decimalAmount.setScale(scale, RoundingMode.HALF_UP);

        if (currency.getCurrencyType() == CurrencyType.FIAT) {
            return formatFiat(decimalAmount, currency);
        }

        return formatCrypto(decimalAmount, currency);
    }

    private BigDecimal toBigDecimal(FastMoney money) {
        long rawAmount = money.amount();
        int precision = Math.max(0, money.getPrecision());

        if (precision == 0) {
            return BigDecimal.valueOf(rawAmount);
        }

        return BigDecimal.valueOf(rawAmount, precision);
    }

    private int scaleFor(Currency currency, int moneyPrecision) {
        if (currency.getCurrencyType() == CurrencyType.FIAT) {
            return Math.max(0, currency.getFractionalDigits());
        }

        return Math.max(
                Math.max(0, currency.getFractionalDigits()),
                Math.max(0, moneyPrecision)
        );
    }

    private String formatFiat(BigDecimal amount, Currency currency) {
        String symbol = safe(currency.getSymbol());
        String value = amount.toPlainString();

        if (symbol.isBlank()) {
            return value + " " + currency.getCode();
        }

        if (amount.signum() < 0) {
            return "-" + symbol + amount.abs().toPlainString();
        }

        return symbol + value;
    }

    private String formatCrypto(BigDecimal amount, Currency currency) {
        return amount.stripTrailingZeros().toPlainString() + " " + currency.getCode();
    }

    /**
     * Parses formatted money text into a SpinnerNumberModel.
     *
     * <p>This supports values like:</p>
     * <ul>
     *     <li>$1,234.56</li>
     *     <li>- $1,234.56</li>
     *     <li>0.0025 BTC</li>
     *     <li>1,000.00 USD</li>
     * </ul>
     */
    public SpinnerNumberModel parse(String text) {
        BigDecimal value = parseNumber(text);
        return new SpinnerNumberModel(
                value,
                null,
                null,
                BigDecimal.ONE.movePointLeft(Math.min(DEFAULT_PARSE_SCALE, Math.max(0, value.scale())))
        );
    }

    public BigDecimal parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }

        String cleaned = text.trim()
                .replace(",", "")
                .replaceAll("\\s+", "");

        boolean negative = cleaned.startsWith("-") || cleaned.startsWith("(");

        cleaned = cleaned
                .replace("(", "")
                .replace(")", "")
                .replaceAll("[^0-9.\\-]", "");

        if (cleaned.isBlank() || "-".equals(cleaned) || ".".equals(cleaned) || "-.".equals(cleaned)) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal parsed = new BigDecimal(cleaned.replace("-", ""));
            return negative ? parsed.negate() : parsed;
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}