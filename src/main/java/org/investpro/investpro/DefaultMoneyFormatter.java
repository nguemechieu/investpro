package org.investpro.investpro;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

import static java.lang.Math.min;

/**
 * @author NOEL NGUEMECHIEU
 */
public final class DefaultMoneyFormatter implements MoneyFormatter<Money> {
    public static final DefaultMoneyFormatter DEFAULT_FIAT_FORMATTER = new Builder()
            .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(false)
            .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
            .displayAtLeastAllFractionalDigits(true)
            .build();

    public static final DefaultMoneyFormatter DEFAULT_CRYPTO_FORMATTER = new Builder()
            .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
            .useDigitGroupingSeparator(true)
            .useASpaceBetweenCurrencyAndAmount(true)
            .forceDecimalPoint()
            .trimTrailingZerosAfterDecimalPoint()
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

    private DefaultMoneyFormatter(@NotNull Builder builder) {
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
            numberFormat = NumberFormat.getCurrencyInstance(locale);
        } else {
            decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.getDefault());
            numberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
        }

    }

    private static int integerDigits(BigDecimal n) {
        Objects.requireNonNull(n);
        return n.signum() == 0 ? 1 : n.precision() - n.scale();
    }

    private static String addDigitGroupingSeparator(String number, char groupingSeparator, int groupAmount) {
        Objects.requireNonNull(number);
        StringBuilder groupSeparatedStringBuilder = new StringBuilder();
        int digitCount = 0;
        for (int i = number.length() - 1; i >= 0; i--) {
            groupSeparatedStringBuilder.append(number.charAt(i));
            digitCount++;
            if (digitCount == groupAmount && !(i == number.length() - 1)) {
                groupSeparatedStringBuilder.append(groupingSeparator);
                digitCount = 0;
            }
        }

        return groupSeparatedStringBuilder.reverse().toString();
    }

    public String format(Money money) {
        return format((DefaultMoney) money);
    }

    public String format(DefaultMoney defaultMoney) {
        Objects.requireNonNull(defaultMoney, "defaultMoney must not be null");
        String prefix = "";
        String decimalPointSeparator = "";
        String suffix = "";

        switch (currencyPosition) {
            case BEFORE_AMOUNT:
                prefix = defaultMoney.currency().getCode();

                if (currencyStyle == CurrencyStyle.SYMBOL && !defaultMoney.currency().getSymbol().isEmpty()) {
                    prefix = defaultMoney.currency().getSymbol();
                }
                break;
            case AFTER_AMOUNT:
                suffix = defaultMoney.currency().getCode();

                if (currencyStyle == CurrencyStyle.SYMBOL && !defaultMoney.currency().getSymbol().isEmpty()) {
                    suffix = defaultMoney.currency().getSymbol();
                }
                break;
            default:
                throw new IllegalArgumentException("unknown currency position: " + currencyPosition);
        }

        StringBuilder numberBeforeDecimalPointBuilder = new StringBuilder();
        StringBuilder numberAfterDecimalPointBuilder = new StringBuilder();
        // http://stackoverflow.com/questions/18828377/biginteger-count-the-number-of-decimal-digits-in-a-scalable-method
        int numDigitsBeforeDecimalPoint;
        if (defaultMoney.amount().toBigInteger().compareTo(BigInteger.ZERO) > 0) {
            numDigitsBeforeDecimalPoint = integerDigits(defaultMoney.amount());
        } else {
            numDigitsBeforeDecimalPoint = 1;
        }

        numberBeforeDecimalPointBuilder.append("0".repeat(Math.max(0, numDigitsBeforeDecimalPoint)));

        if (defaultMoney.amount().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0 &&
                !displayAtLeastAllFractionalDigits) {
            // the number has no fractional digits
            if (forceDecimalPoint) {
                if (Objects.equals(defaultMoney.currency().getCurrencyType(), CurrencyType.FIAT.name())) {
                    if (locale == null) {
                        decimalPointSeparator = ".";
                    } else {
                        decimalPointSeparator = String.valueOf(decimalFormatSymbols.getMonetaryDecimalSeparator());
                    }
                    numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, (fractionalDigitsCap == -1 ?
                            defaultMoney.currency().getFractionalDigits() :
                            min(defaultMoney.currency().getFractionalDigits(), fractionalDigitsCap)))));
                } else {
                    if (fractionalDigitsCap == 0) {
                        decimalPointSeparator = "";
                    } else {
                        if (locale == null) {
                            decimalPointSeparator = ".";
                        } else {
                            decimalPointSeparator = String.valueOf(decimalFormatSymbols.getMonetaryDecimalSeparator());
                        }

                        if (wholeNumberFractionalDigitAmount == WholeNumberFractionalDigitAmount.MIN) {
                            numberAfterDecimalPointBuilder.append('0');
                        } else if (wholeNumberFractionalDigitAmount == WholeNumberFractionalDigitAmount.MAX) {
                            numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, (fractionalDigitsCap == -1 ?
                                    defaultMoney.currency().getFractionalDigits() :
                                    min(defaultMoney.currency().getFractionalDigits(), fractionalDigitsCap)))));
                        }
                    }
                }
            }
        } else {
            // the given number has a non-zero fractional part or displayAtLeastAllFractionalDigits is true
            if (!forceDecimalPoint && fractionalDigitsCap == 0) {
                decimalPointSeparator = "";
            } else {
                if (locale == null) {
                    decimalPointSeparator = ".";
                } else {
                    decimalPointSeparator = String.valueOf(decimalFormatSymbols.getMonetaryDecimalSeparator());
                }

                int numDigitsAfterDecimalPoint;

                if (trimTrailingZerosAfterDecimalPoint) {
                    String trimmedNumber =
                            defaultMoney.amount().toPlainString().replaceFirst("\\.0*$|(\\.\\d*?)0+$", "$1");
                    numDigitsAfterDecimalPoint = trimmedNumber.substring(trimmedNumber.indexOf(".")).length() - 1;
                } else {
                    numDigitsAfterDecimalPoint = Math.max(0, defaultMoney.amount().scale());
                }

                if (unlimitedFractionalDigits) {
                    numberAfterDecimalPointBuilder.append("0".repeat(Math.max(0, numDigitsAfterDecimalPoint)));
                } else {
                    int endIndex = displayAtLeastAllFractionalDigits ? defaultMoney.currency().getFractionalDigits()
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

        if (useDigitGroupingSeparator) {
            if (locale == null) {
                numberBeforeDecimalPoint = addDigitGroupingSeparator(numberBeforeDecimalPoint, ',', 3);
            } else {
                // TODO not all locales use the same groupingAmount, specifically Indic languages, a good list is
                // here: http://static-bugzilla.wikimedia.org/show_bug.cgi?id=29495
                // if we ever care enough we will expand this switch to handle these cases
                final int groupAmount = switch (locale.getISO3Language()) { // Assamese
                    // Bengali
                    case "asm", "ben", "mar" -> // Marathi
                            2;
                    default -> 3;
                };
                numberBeforeDecimalPoint = addDigitGroupingSeparator(numberBeforeDecimalPoint,
                        decimalFormatSymbols.getGroupingSeparator(), groupAmount);
            }
        }

        String number = numberBeforeDecimalPoint + decimalPointSeparator + numberAfterDecimalPoint;

        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setRoundingMode(roundingMode);

            if (prefix.isEmpty() && suffix.isEmpty()) {
                if (locale == null) {
                    ((DecimalFormat) numberFormat).applyPattern(number);
                } else {
                    ((DecimalFormat) numberFormat).applyLocalizedPattern(number);
                }
            } else if (prefix.isEmpty()) {
                if (putSpaceBetweenCurrencyAndAmount) {
                    if (locale == null) {
                        ((DecimalFormat) numberFormat).applyPattern(number + ' ' + suffix);
                    } else {
                        ((DecimalFormat) numberFormat).applyLocalizedPattern(number + ' ' + suffix);
                    }
                } else {
                    if (locale == null) {
                        ((DecimalFormat) numberFormat).applyPattern(number + suffix);
                    } else {
                        ((DecimalFormat) numberFormat).applyLocalizedPattern(number + suffix);
                    }
                }
            } else {
                if (putSpaceBetweenCurrencyAndAmount) {
                    if (locale == null) {
                        ((DecimalFormat) numberFormat).applyPattern(prefix + ' ' + number);
                    } else {
                        ((DecimalFormat) numberFormat).applyLocalizedPattern(prefix + ' ' + number);
                    }
                } else {
                    if (locale == null) {
                        ((DecimalFormat) numberFormat).applyPattern(prefix + number);
                    } else {
                        ((DecimalFormat) numberFormat).applyLocalizedPattern(prefix + number);
                    }
                }
            }
        }

        return numberFormat.format(defaultMoney.amount());
    }

    public CurrencyStyle getCurrencyStyle() {
        return currencyStyle;
    }

    public CurrencyPosition getCurrencyPosition() {
        return currencyPosition;
    }

    public static class Builder {
        private boolean useCurrencySymbol;
        private boolean useCurrencyCode;
        private boolean putSpaceBetweenCurrencyAndAmount = true;
        private boolean useDigitGroupingSeparator;
        private CurrencyPosition currencyPosition = CurrencyPosition.BEFORE_AMOUNT;
        private boolean trimTrailingZerosAfterDecimalPoint;
        private boolean forceDecimalPoint;
        private WholeNumberFractionalDigitAmount wholeNumberFractionalDigitAmount =
                WholeNumberFractionalDigitAmount.MIN;
        private Locale locale;
        private RoundingMode roundingMode = RoundingMode.HALF_EVEN;
        private boolean unlimitedFractionalDigits;
        private boolean displayAtLeastAllFractionalDigits;
        private int fractionalDigitsCap = -1;

        public Builder withDefaultsFor(CurrencyType currencyType) {
            return switch (currencyType) {
                case CRYPTO -> withCurrencyCode(DEFAULT_CRYPTO_FORMATTER.currencyPosition)
                        .useDigitGroupingSeparator(DEFAULT_CRYPTO_FORMATTER.useDigitGroupingSeparator)
                        .useASpaceBetweenCurrencyAndAmount(
                                DEFAULT_CRYPTO_FORMATTER.putSpaceBetweenCurrencyAndAmount)
                        .forceDecimalPoint()
                        .trimTrailingZerosAfterDecimalPoint();
                case FIAT -> withCurrencySymbol(DEFAULT_FIAT_FORMATTER.currencyPosition)
                        .useDigitGroupingSeparator(DEFAULT_FIAT_FORMATTER.useDigitGroupingSeparator)
                        .useASpaceBetweenCurrencyAndAmount(DEFAULT_FIAT_FORMATTER.putSpaceBetweenCurrencyAndAmount)
                        .forceDecimalPoint(DEFAULT_FIAT_FORMATTER.wholeNumberFractionalDigitAmount)
                        .displayAtLeastAllFractionalDigits(
                                DEFAULT_FIAT_FORMATTER.displayAtLeastAllFractionalDigits);
                default -> throw new IllegalArgumentException("unknown currency type: " + currencyType);
            };
        }

        public Builder withCurrencySymbol() {
            return withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT);
        }

        public Builder withCurrencySymbol(CurrencyPosition position) {
            Objects.requireNonNull(position, "position must not be null");
            useCurrencySymbol = true;
            currencyPosition = position;
            return this;
        }

        /**
         * Display the amount with the currency code of the currency of the
         * {@code DefaultMoney} we are formatting. This is the default used
         * when either code or symbol is not specified. The code will be displayed
         * after the amount.
         *
         * @return the Builder instance
         */
        public Builder withCurrencyCode() {
            return withCurrencyCode(CurrencyPosition.AFTER_AMOUNT);
        }

        public Builder withCurrencyCode(CurrencyPosition position) {
            Objects.requireNonNull(position, "position must not be null");
            useCurrencyCode = true;
            currencyPosition = position;
            return this;
        }

        public Builder useASpaceBetweenCurrencyAndAmount(boolean putSpaceBetweenCurrencyAndAmount) {
            this.putSpaceBetweenCurrencyAndAmount = putSpaceBetweenCurrencyAndAmount;
            return this;
        }

        public Builder useDigitGroupingSeparator(boolean useDigitGroupingSeparator) {
            this.useDigitGroupingSeparator = useDigitGroupingSeparator;
            return this;
        }

        public Builder trimTrailingZerosAfterDecimalPoint() {
            trimTrailingZerosAfterDecimalPoint = true;
            return this;
        }

        public Builder capFractionalDigitsTo(int cap) {
            if (cap < 0) {
                throw new IllegalArgumentException("cap must be >= 0");
            }
            fractionalDigitsCap = cap;
            return this;
        }

        public Builder forceDecimalPoint() {
            return forceDecimalPoint(WholeNumberFractionalDigitAmount.MIN);
        }

        /**
         * Forces a decimal point to be displayed. The number of 0's (placeholder
         * digits) after the decimal point can be set via {@code wholeNumberFractionalDigitAmount},
         * where {@code WholeNumberFractionalDigitAmount.MIN} specifies exactly one
         * zero (0), and {@code WholeNumberFractionalDigitAmount.MAX} specifies the
         * N zeros, where N is the number of fractional digits of the currency of
         * the {@code DefaultMoney} we are formatting.
         *
         * @param wholeNumberFractionalDigitAmount either MIN or MAX
         * @return the Builder instance
         */
        public Builder forceDecimalPoint(WholeNumberFractionalDigitAmount wholeNumberFractionalDigitAmount) {
            forceDecimalPoint = true;
            this.wholeNumberFractionalDigitAmount = wholeNumberFractionalDigitAmount;
            return this;
        }

        public Builder applyLocaleSettings(Locale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * Specify the rounding mode to be used when formatting the number.
         * By default, this is set to {@code RoundingMode.HALF_EVEN}.
         *
         * @param roundingMode the {@code RoundingMode} used to format the
         *                     DefaultMoney
         * @return the Builder instance
         */
        public Builder withRounding(RoundingMode roundingMode) {
            this.roundingMode = roundingMode;
            return this;
        }

        /**
         * If set to true, the number of fractional digits (digits past the decimal
         * separator) is not capped to the number of fractional digits of the currency
         * of the {@code DefaultMoney} we are formatting. By default, this is false.
         *
         * @param unlimitedFractionalDigits
         * @return
         */
        public Builder withUnlimitedFractionalDigits(boolean unlimitedFractionalDigits) {
            this.unlimitedFractionalDigits = unlimitedFractionalDigits;
            return this;
        }

        /**
         * If set to true, and the number has a non-zero fractional part, then the number
         * of digits after the decimal point will be <em>at least</em> N, where N is
         * the number of fractional digits of the currency we are formatting.
         * <p>
         * For example, if the currency is USD (which has two fractional digits) and the
         * number we are formatting is 566.3, then the number will be formatted to 566.30.
         *
         * @param displayAtLeastAllFractionalDigits
         * @return
         */
        public Builder displayAtLeastAllFractionalDigits(boolean displayAtLeastAllFractionalDigits) {
            this.displayAtLeastAllFractionalDigits = displayAtLeastAllFractionalDigits;
            return this;
        }

        public DefaultMoneyFormatter build() {
            if (useCurrencySymbol && useCurrencyCode) {
                throw new IllegalArgumentException("useCurrencyCode and useCurrencySymbol are both set");
            } else if (!useCurrencySymbol && !useCurrencyCode) {
                // none are set, default to code
                useCurrencyCode = true;
            }

            return new DefaultMoneyFormatter(this);
        }
    }
}
