package org.investpro.models.currency;

import org.investpro.enums.CurrencyPosition;
import org.investpro.utils.WholeNumberFractionalDigitAmount;

import static java.lang.Math.min;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Reliable formatter for Money objects supporting fiat, crypto, forex, stocks,
 * futures, and trading P&L.
 * <p>
 * Features:
 * <ul>
 * <li>Proper digit grouping separators (including Indian/Indic grouping:
 * 1,23,45,678)</li>
 * <li>Correct negative amount handling</li>
 * <li>Configurable fractional digit caps via
 * {@code capFractionalDigits(int)}</li>
 * <li>Manual currency symbol/code control via
 * {@code NumberFormat.getNumberInstance()}</li>
 * <li>Pre-configured formatters for common use cases</li>
 * <li>Full null safety and validation</li>
 * </ul>
 *
 * @author NOEL NGUEMECHIEU
 */
public final class DefaultMoneyFormatter implements MoneyFormatter<Money> {

    /**
     * Default formatter for fiat currencies (USD, EUR, etc.)
     */
    public static final DefaultMoneyFormatter DEFAULT_FIAT_FORMATTER = new Builder()
            .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(false)
            .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
            .displayAtLeastAllFractionalDigits(true)
            .build();

    /**
     * Default formatter for cryptocurrencies (BTC, ETH, etc.)
     */
    public static final DefaultMoneyFormatter DEFAULT_CRYPTO_FORMATTER = new Builder()
            .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(true)
            .forceDecimalPoint()
            .trimTrailingZerosAfterDecimalPoint()
            .capFractionalDigits(8)
            .build();

    /**
     * Default formatter for forex pairs (EUR/USD, GBP/JPY, etc.)
     */
    public static final DefaultMoneyFormatter DEFAULT_FOREX_FORMATTER = new Builder()
            .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(true)
            .forceDecimalPoint()
            .displayAtLeastAllFractionalDigits(true)
            .capFractionalDigits(5)
            .build();

    /**
     * Default formatter for stocks (US stocks, etc.)
     */
    public static final DefaultMoneyFormatter DEFAULT_STOCK_FORMATTER = new Builder()
            .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(false)
            .forceDecimalPoint()
            .displayAtLeastAllFractionalDigits(true)
            .capFractionalDigits(2)
            .build();

    /**
     * Default formatter for futures contracts
     */
    public static final DefaultMoneyFormatter DEFAULT_FUTURES_FORMATTER = new Builder()
            .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(true)
            .forceDecimalPoint()
            .trimTrailingZerosAfterDecimalPoint()
            .capFractionalDigits(4)
            .build();

    private final CurrencyStyle currencyStyle;
    private final CurrencyPosition currencyPosition;
    private final boolean putSpaceBetweenCurrencyAndAmount;
    private final boolean useDigitGroupingSeparator;
    private final boolean trimTrailingZerosAfterDecimalPoint;
    private final boolean forceDecimalPoint;
    private final WholeNumberFractionalDigitAmount wholeNumberFractionalDigitAmount;
    private final RoundingMode roundingMode;
    private final Locale locale;
    private final DecimalFormatSymbols decimalFormatSymbols;
    private final NumberFormat numberFormat;
    private final boolean unlimitedFractionalDigits;
    private final boolean displayAtLeastAllFractionalDigits;
    private final int fractionalDigitsCap;

    private DefaultMoneyFormatter(Builder builder) {
        currencyStyle = builder.useCurrencySymbol ? CurrencyStyle.SYMBOL : CurrencyStyle.CODE;
        currencyPosition = builder.currencyPosition;
        putSpaceBetweenCurrencyAndAmount = builder.putSpaceBetweenCurrencyAndAmount;
        useDigitGroupingSeparator = builder.useDigitGroupingSeparator;
        trimTrailingZerosAfterDecimalPoint = builder.trimTrailingZerosAfterDecimalPoint;
        forceDecimalPoint = builder.forceDecimalPoint;
        wholeNumberFractionalDigitAmount = builder.wholeNumberFractionalDigitAmount;
        roundingMode = builder.roundingMode;
        locale = builder.locale;
        unlimitedFractionalDigits = builder.unlimitedFractionalDigits;
        displayAtLeastAllFractionalDigits = builder.displayAtLeastAllFractionalDigits;
        fractionalDigitsCap = builder.fractionalDigitsCap;

        if (locale != null) {
            decimalFormatSymbols = DecimalFormatSymbols.getInstance(locale);
            numberFormat = NumberFormat.getNumberInstance(locale);
        } else {
            decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.getDefault());
            numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
        }
    }

    /**
     * Formats a Money object into a string representation.
     *
     * @param money the money object to format
     * @return formatted string with currency and proper separators
     * @throws NullPointerException if money is null
     */
    public String format(Money money) {
        return format((DefaultMoney) money);
    }

    /**
     * Formats a DefaultMoney object into a string representation.
     *
     * @param defaultMoney the money object to format
     * @return formatted string with currency and proper separators
     * @throws NullPointerException if defaultMoney or its properties are null
     */
    public String format(DefaultMoney defaultMoney) {
        Objects.requireNonNull(defaultMoney, "defaultMoney must not be null");
        Objects.requireNonNull(defaultMoney.currency(), "currency must not be null");
        Objects.requireNonNull(defaultMoney.amount(), "amount must not be null");

        // Extract and handle the sign
        boolean isNegative = defaultMoney.amount().signum() < 0;
        BigDecimal absoluteAmount = defaultMoney.amount().abs();

        String prefix = "";
        String suffix = "";
        String decimalPointSeparator = "";

        // Build currency prefix or suffix
        switch (currencyPosition) {
            case BEFORE_AMOUNT:
                prefix = defaultMoney.currency().getCode();
                if (currencyStyle == CurrencyStyle.SYMBOL && defaultMoney.currency().getSymbol() != null
                        && !defaultMoney.currency().getSymbol().isEmpty()) {
                    prefix = defaultMoney.currency().getSymbol();
                }
                break;
            case AFTER_AMOUNT:
                suffix = defaultMoney.currency().getCode();
                if (currencyStyle == CurrencyStyle.SYMBOL && defaultMoney.currency().getSymbol() != null
                        && !defaultMoney.currency().getSymbol().isEmpty()) {
                    suffix = defaultMoney.currency().getSymbol();
                }
                break;
            default:
                throw new IllegalArgumentException("unknown currency position: " + currencyPosition);
        }

        // Calculate number of digits before decimal point
        int numDigitsBeforeDecimalPoint;
        if (absoluteAmount.toBigInteger().compareTo(BigInteger.ZERO) > 0) {
            numDigitsBeforeDecimalPoint = integerDigits(absoluteAmount);
        } else {
            numDigitsBeforeDecimalPoint = 1;
        }

        StringBuilder numberBeforeDecimalPointBuilder = new StringBuilder();
        numberBeforeDecimalPointBuilder.append("0".repeat(Math.max(0, numDigitsBeforeDecimalPoint)));

        StringBuilder numberAfterDecimalPointBuilder = new StringBuilder();

        // Handle decimal point and fractional digits
        if (absoluteAmount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
                && !displayAtLeastAllFractionalDigits) {
            // Number has no fractional digits
            if (forceDecimalPoint) {
                if (defaultMoney.currency().getCurrencyType() == CurrencyType.FIAT) {
                    decimalPointSeparator = getDecimalSeparator();
                    int fiatFractionalDigits = fractionalDigitsCap == -1
                            ? defaultMoney.currency().getFractionalDigits()
                            : min(defaultMoney.currency().getFractionalDigits(), fractionalDigitsCap);
                    numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, fiatFractionalDigits)));
                } else {
                    // Non-FIAT (crypto, etc.)
                    if (fractionalDigitsCap == 0) {
                        decimalPointSeparator = "";
                    } else {
                        decimalPointSeparator = getDecimalSeparator();
                        if (wholeNumberFractionalDigitAmount == WholeNumberFractionalDigitAmount.MIN) {
                            numberAfterDecimalPointBuilder.append('0');
                        } else if (wholeNumberFractionalDigitAmount == WholeNumberFractionalDigitAmount.MAX) {
                            int cryptoFractionalDigits = fractionalDigitsCap == -1
                                    ? defaultMoney.currency().getFractionalDigits()
                                    : min(defaultMoney.currency().getFractionalDigits(), fractionalDigitsCap);
                            numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, cryptoFractionalDigits)));
                        }
                    }
                }
            }
        } else {
            // Number has a non-zero fractional part or displayAtLeastAllFractionalDigits is
            // true
            if (!forceDecimalPoint && fractionalDigitsCap == 0) {
                decimalPointSeparator = "";
            } else {
                decimalPointSeparator = getDecimalSeparator();

                int numDigitsAfterDecimalPoint;
                if (trimTrailingZerosAfterDecimalPoint) {
                    String trimmedNumber = absoluteAmount.toPlainString()
                            .replaceFirst("\\.0*$|(\\. \\d*?)0+$", "$1");
                    int dotIndex = trimmedNumber.indexOf('.');
                    numDigitsAfterDecimalPoint = dotIndex >= 0
                            ? trimmedNumber.substring(dotIndex).length() - 1
                            : 0;
                } else {
                    numDigitsAfterDecimalPoint = Math.max(0, absoluteAmount.scale());
                }

                if (unlimitedFractionalDigits) {
                    numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, numDigitsAfterDecimalPoint)));
                } else {
                    int endIndex = displayAtLeastAllFractionalDigits
                            ? defaultMoney.currency().getFractionalDigits()
                            : min(numDigitsAfterDecimalPoint, defaultMoney.currency().getFractionalDigits());

                    if (fractionalDigitsCap != -1) {
                        endIndex = min(fractionalDigitsCap, endIndex);
                    }
                    numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, endIndex)));
                }
            }
        }

        String numberBeforeDecimalPoint = numberBeforeDecimalPointBuilder.toString();
        String numberAfterDecimalPoint = numberAfterDecimalPointBuilder.toString();

        // Apply digit grouping separator
        if (useDigitGroupingSeparator) {
            numberBeforeDecimalPoint = applyDigitGrouping(numberBeforeDecimalPoint);
        }

        String number = numberBeforeDecimalPoint + decimalPointSeparator + numberAfterDecimalPoint;

        // Add negative sign if needed
        if (isNegative) {
            number = "-" + number;
        }

        // Apply pattern to NumberFormat
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setRoundingMode(roundingMode);
            String pattern = buildPattern(number, prefix, suffix);
            applyPatternToFormat((DecimalFormat) numberFormat, pattern);
        }

        return numberFormat.format(absoluteAmount);
    }

    /**
     * Gets the appropriate decimal separator for the locale.
     *
     * @return decimal separator character as string
     */
    private String getDecimalSeparator() {
        if (locale == null) {
            return ".";
        }
        return String.valueOf(decimalFormatSymbols.getMonetaryDecimalSeparator());
    }

    /**
     * Applies digit grouping separators using proper locale-based grouping.
     * Supports Indian/Indic grouping (1,23,45,678) for appropriate locales.
     *
     * @param number the numeric string (without decimal part)
     * @return the number with appropriate grouping separators
     */
    private String applyDigitGrouping(String number) {
        Objects.requireNonNull(number);

        char groupingSeparator = locale != null
                ? decimalFormatSymbols.getGroupingSeparator()
                : ',';

        int groupAmount = getGroupingAmount();

        return addDigitGroupingSeparator(number, groupingSeparator, groupAmount);
    }

    /**
     * Determines the digit grouping amount based on locale.
     * Returns 2 for Indian/Indic languages, 3 for others.
     *
     * @return the grouping amount (2 for Indian languages, 3 for others)
     */
    private int getGroupingAmount() {
        if (locale == null) {
            return 3;
        }

        String iso3Language = locale.getISO3Language();

        // Indian/Indic language grouping uses 2 digits
        return switch (iso3Language) {
            case "asm" -> 2; // Assamese
            case "ben" -> 2; // Bengali
            case "guj" -> 2; // Gujarati
            case "hin" -> 2; // Hindi
            case "kan" -> 2; // Kannada
            case "mal" -> 2; // Malayalam
            case "mar" -> 2; // Marathi
            case "ory" -> 2; // Odia
            case "pan" -> 2; // Punjabi
            case "tam" -> 2; // Tamil
            case "tel" -> 2; // Telugu
            default -> 3; // Standard Western grouping
        };
    }

    /**
     * Builds the pattern string for the NumberFormat.
     *
     * @param number the formatted number
     * @param prefix prefix (currency)
     * @param suffix suffix (currency)
     * @return the pattern string
     */
    private String buildPattern(String number, String prefix, String suffix) {
        if (prefix.isEmpty() && suffix.isEmpty()) {
            return number;
        } else if (prefix.isEmpty()) {
            return putSpaceBetweenCurrencyAndAmount
                    ? number + ' ' + suffix
                    : number + suffix;
        } else {
            return putSpaceBetweenCurrencyAndAmount
                    ? prefix + ' ' + number
                    : prefix + number;
        }
    }

    /**
     * Applies a pattern to the DecimalFormat, choosing between applyPattern and
     * applyLocalizedPattern.
     *
     * @param format  the DecimalFormat to update
     * @param pattern the pattern string
     */
    private void applyPatternToFormat(DecimalFormat format, String pattern) {
        Objects.requireNonNull(format);
        Objects.requireNonNull(pattern);
        if (locale == null) {
            format.applyPattern(pattern);
        } else {
            format.applyLocalizedPattern(pattern);
        }
    }

    /**
     * Counts the number of integer digits in a BigDecimal.
     *
     * @param n the BigDecimal value
     * @return the count of digits before the decimal point
     * @throws NullPointerException if n is null
     */
    private static int integerDigits(BigDecimal n) {
        Objects.requireNonNull(n);
        return n.signum() == 0 ? 1 : n.precision() - n.scale();
    }

    /**
     * Adds digit grouping separators to a numeric string.
     * Handles both standard (every 3 digits) and Indian (2, 2, 3 pattern) grouping.
     *
     * @param number            the numeric string
     * @param groupingSeparator the separator character
     * @param groupAmount       the number of digits per group (2 or 3)
     * @return the string with grouping separators applied
     * @throws NullPointerException if number is null
     */
    private static String addDigitGroupingSeparator(String number, char groupingSeparator, int groupAmount) {
        Objects.requireNonNull(number);

        if (number.length() <= groupAmount) {
            return number;
        }

        StringBuilder result = new StringBuilder();
        int length = number.length();

        if (groupAmount == 2) {
            // Indian grouping: first group is 3 digits, then groups of 2
            // For example: 1,23,45,678
            result.append(number, 0, length % 2 == 1 ? 1 : 2);
            for (int i = length % 2 == 1 ? 1 : 2; i < length; i += 2) {
                result.append(groupingSeparator);
                result.append(number, i, Math.min(i + 2, length));
            }
        } else {
            // Standard Western grouping: every 3 digits from right
            int count = 0;
            for (int i = length - 1; i >= 0; i--) {
                if (count > 0 && count % groupAmount == 0) {
                    result.insert(0, groupingSeparator);
                }
                result.insert(0, number.charAt(i));
                count++;
            }
        }

        return result.toString();
    }

    /**
     * Currency style enum for controlling how currency is displayed.
     */
    enum CurrencyStyle {
        SYMBOL,
        CODE
    }

    /**
     * Builder class for constructing DefaultMoneyFormatter instances with fluent
     * API.
     */
    public static class Builder {
        private final RoundingMode roundingMode = RoundingMode.HALF_EVEN;
        private boolean useCurrencySymbol;
        private boolean useCurrencyCode;
        private boolean putSpaceBetweenCurrencyAndAmount = true;
        private boolean useDigitGroupingSeparator;
        private CurrencyPosition currencyPosition = CurrencyPosition.BEFORE_AMOUNT;
        private boolean trimTrailingZerosAfterDecimalPoint;
        private boolean forceDecimalPoint;
        private WholeNumberFractionalDigitAmount wholeNumberFractionalDigitAmount = WholeNumberFractionalDigitAmount.MIN;
        private int fractionalDigitsCap = -1;
        private boolean unlimitedFractionalDigits;
        private boolean displayAtLeastAllFractionalDigits;
        private Locale locale;

        /**
         * Sets the formatter to use the currency symbol and specify its position.
         *
         * @param position the position of the currency symbol (BEFORE_AMOUNT or
         *                 AFTER_AMOUNT)
         * @return this builder instance
         * @throws NullPointerException if position is null
         */
        public Builder withCurrencySymbol(CurrencyPosition position) {
            Objects.requireNonNull(position, "position must not be null");
            useCurrencySymbol = true;
            useCurrencyCode = false;
            currencyPosition = position;
            return this;
        }

        /**
         * Sets the formatter to use the currency code and specify its position.
         *
         * @param position the position of the currency code (BEFORE_AMOUNT or
         *                 AFTER_AMOUNT)
         * @return this builder instance
         * @throws NullPointerException if position is null
         */
        public Builder withCurrencyCode(CurrencyPosition position) {
            Objects.requireNonNull(position, "position must not be null");
            useCurrencyCode = true;
            useCurrencySymbol = false;
            currencyPosition = position;
            return this;
        }

        /**
         * Sets whether to use a space between the currency and amount.
         *
         * @param putSpace true to add a space, false otherwise
         * @return this builder instance
         */
        public Builder useASpaceBetweenCurrencyAndAmount(boolean putSpace) {
            this.putSpaceBetweenCurrencyAndAmount = putSpace;
            return this;
        }

        /**
         * Sets whether to use digit grouping separators (e.g., 1,234,567).
         *
         * @param use true to use grouping separators, false otherwise
         * @return this builder instance
         */
        public Builder useDigitGroupingSeparator(boolean use) {
            this.useDigitGroupingSeparator = use;
            return this;
        }

        /**
         * Trims trailing zeros after the decimal point.
         * For example, 123.4500 becomes 123.45.
         *
         * @return this builder instance
         */
        public Builder trimTrailingZerosAfterDecimalPoint() {
            this.trimTrailingZerosAfterDecimalPoint = true;
            return this;
        }

        /**
         * Forces a decimal point to be displayed with at least one zero.
         *
         * @return this builder instance
         */
        public Builder forceDecimalPoint() {
            return forceDecimalPoint(WholeNumberFractionalDigitAmount.MIN);
        }

        /**
         * Forces a decimal point to be displayed with a specified number of zeros.
         *
         * @param wholeNumberFractionalDigitAmount MIN for exactly one zero, MAX for
         *                                         currency's fractional digits
         * @return this builder instance
         * @throws NullPointerException if wholeNumberFractionalDigitAmount is null
         */
        public Builder forceDecimalPoint(WholeNumberFractionalDigitAmount wholeNumberFractionalDigitAmount) {
            Objects.requireNonNull(wholeNumberFractionalDigitAmount,
                    "wholeNumberFractionalDigitAmount must not be null");
            forceDecimalPoint = true;
            this.wholeNumberFractionalDigitAmount = wholeNumberFractionalDigitAmount;
            return this;
        }

        /**
         * Ensures at least the currency's standard number of fractional digits are
         * displayed.
         * For example, USD (2 fractional digits) displays 566.3 as 566.30.
         *
         * @param display true to enforce minimum fractional digits, false otherwise
         * @return this builder instance
         */
        public Builder displayAtLeastAllFractionalDigits(boolean display) {
            this.displayAtLeastAllFractionalDigits = display;
            return this;
        }

        /**
         * Caps the maximum number of fractional digits displayed.
         * Useful for cryptocurrencies and other assets with variable precision.
         *
         * @param cap the maximum number of fractional digits (-1 for no cap)
         * @return this builder instance
         * @throws IllegalArgumentException if cap is less than -1
         */
        public Builder capFractionalDigits(int cap) {
            if (cap < -1) {
                throw new IllegalArgumentException("fractionalDigitsCap must be >= -1, got: " + cap);
            }
            this.fractionalDigitsCap = cap;
            return this;
        }

        /**
         * Allows unlimited fractional digits (no cap).
         * Overrides capFractionalDigits().
         *
         * @return this builder instance
         */
        public Builder unlimitedFractionalDigits() {
            this.unlimitedFractionalDigits = true;
            return this;
        }

        /**
         * Sets the locale for number and currency formatting.
         *
         * @param locale the locale to use
         * @return this builder instance
         */
        public Builder withLocale(Locale locale) {
            Objects.requireNonNull(locale, "locale must not be null");
            this.locale = locale;
            return this;
        }

        /**
         * Builds the DefaultMoneyFormatter instance.
         *
         * @return a new DefaultMoneyFormatter with the configured settings
         * @throws IllegalArgumentException if both currency symbol and code are set
         */
        public DefaultMoneyFormatter build() {
            if (useCurrencySymbol && useCurrencyCode) {
                throw new IllegalArgumentException(
                        "useCurrencyCode and useCurrencySymbol are both set; choose one");
            } else if (!useCurrencySymbol && !useCurrencyCode) {
                // Default to code if neither is set
                useCurrencyCode = true;
            }

            return new DefaultMoneyFormatter(this);
        }
    }
}
