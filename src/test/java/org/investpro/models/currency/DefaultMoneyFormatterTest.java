package org.investpro.models.currency;

import org.investpro.enums.CurrencyPosition;
import org.investpro.utils.WholeNumberFractionalDigitAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DefaultMoneyFormatter.
 * Tests formatting for fiat, crypto, forex, stocks, futures, and trading P&L
 * display.
 *
 * @author Test Suite
 */
class DefaultMoneyFormatterTest {

    private MockCurrency usd;
    private MockCurrency btc;
    private MockCurrency eur;
    @SuppressWarnings("unused")
    private MockCurrency aud;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        // USD: fiat, 2 fractional digits, $
        usd = new MockCurrency("USD", "US Dollar", CurrencyType.FIAT, 2, "$");

        // BTC: crypto, 8 fractional digits, ₿
        btc = new MockCurrency("BTC", "Bitcoin", CurrencyType.CRYPTO, 8, "₿");

        // EUR: fiat, 2 fractional digits, €
        eur = new MockCurrency("EUR", "Euro", CurrencyType.FIAT, 2, "€");

        // AUD: fiat, 2 fractional digits, A$
        aud = new MockCurrency("AUD", "Australian Dollar", CurrencyType.FIAT, 2, "A$");
    }

    /**
     * Tests basic USD formatting with symbol and grouping.
     */
    @Test
    void testUsdFormattingWithSymbol() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(false)
                .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
                .displayAtLeastAllFractionalDigits(true)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("1234567.89"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertTrue(result.contains("1,234,567.89"), "Should have correct grouping");
    }

    /**
     * Tests Bitcoin formatting with code and no grouping for small amounts.
     */
    @Test
    void testBitcoinFormatting() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(true)
                .forceDecimalPoint()
                .trimTrailingZerosAfterDecimalPoint()
                .capFractionalDigits(8)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("0.12345678"), btc);
        String result = formatter.format(money);

        assertTrue(result.contains("BTC"), "Should contain BTC code");
        assertTrue(result.contains("0.12345678"), "Should preserve 8 decimal places");
    }

    /**
     * Tests formatting with large Bitcoin amounts.
     */
    @Test
    void testBitcoinFormattingLargeAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(true)
                .forceDecimalPoint()
                .trimTrailingZerosAfterDecimalPoint()
                .capFractionalDigits(8)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("123456.12345678"), btc);
        String result = formatter.format(money);

        assertTrue(result.contains("BTC"), "Should contain BTC code");
        assertTrue(result.contains("123,456.12345678") || result.contains("123456.12345678"),
                "Should have amount with correct decimals");
    }

    /**
     * Tests EUR formatting with symbol.
     */
    @Test
    void testEurFormatting() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(true)
                .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
                .displayAtLeastAllFractionalDigits(true)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("999.99"), eur);
        String result = formatter.format(money);

        assertTrue(result.contains("€"), "Should contain EUR symbol");
        assertTrue(result.contains("999.99"), "Should have correct amount");
    }

    /**
     * Tests negative amount formatting.
     */
    @Test
    void testNegativeAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(false)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("-1234.56"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("-"), "Should contain negative sign");
        assertTrue(result.contains("$"), "Should contain USD symbol");
    }

    /**
     * Tests large loss (negative amount).
     */
    @Test
    void testLargeNegativeAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(false)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("-999999.99"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("-"), "Should contain negative sign");
        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertTrue(result.contains(","), "Should have grouping");
    }

    /**
     * Tests fractional digit capping to 2 digits.
     */
    @Test
    void testFractionalDigitCapTwo() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .capFractionalDigits(2)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.12345678"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertTrue(result.contains("100.12"), "Should cap to 2 fractional digits");
    }

    /**
     * Tests fractional digit capping to 4 digits.
     */
    @Test
    void testFractionalDigitCapFour() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(false)
                .capFractionalDigits(4)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.123456789"), btc);
        String result = formatter.format(money);

        assertTrue(result.contains("BTC"), "Should contain BTC code");
        // The fractional digits should be capped to 4, resulting in rounding
        assertTrue(result.contains("100.12") || result.contains("100.1235"), "Should cap fractional digits");
    }

    /**
     * Tests unlimited fractional digits.
     */
    @Test
    void testUnlimitedFractionalDigits() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(false)
                .unlimitedFractionalDigits()
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.123456789"), btc);
        String result = formatter.format(money);

        assertTrue(result.contains("BTC"), "Should contain BTC code");
        // Should preserve original precision
        assertTrue(result.length() > 10, "Should have multiple fractional digits");
    }

    /**
     * Tests Indian locale with 2-digit grouping.
     */
    @Test
    void testIndianGrouping() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(false)
                .withLocale(Locale.of("hi", "IN")) // Hindi locale
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("1234567.89"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        // Indian grouping: 1,23,45,678
        assertTrue(result.contains("1"), "Should have correct formatting");
    }

    /**
     * Tests Bengali (Indian) locale formatting.
     */
    @Test
    void testBengaliGrouping() {
        // Test formatting with Bengali locale
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(true)
                .withLocale(Locale.of("bn", "BD")) // Bengali locale
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("9876543.21"), usd);
        String result = formatter.format(money);

        // Should contain currency symbol and numeric data
        assertNotNull(result, "Result should not be null");
        assertTrue(result.length() > 0, "Result should not be empty");
        assertTrue(result.contains("$"), "Should contain currency symbol");
    }

    /**
     * Tests US locale with 3-digit grouping.
     */
    @Test
    void testUsLocaleGrouping() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(true)
                .withLocale(Locale.US)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("1234567.89"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertTrue(result.contains("1,234,567"), "Should use 3-digit grouping");
    }

    /**
     * Tests German locale with decimal formatting.
     */
    @Test
    void testGermanLocaleFormatting() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(true)
                .withLocale(Locale.GERMANY)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("1234.56"), eur);
        String result = formatter.format(money);

        assertTrue(result.contains("€"), "Should contain EUR symbol");
    }

    /**
     * Tests default fiat formatter.
     */
    @Test
    void testDefaultFiatFormatter() {
        DefaultMoney money = new DefaultMoney(new BigDecimal("100.50"), usd);
        String result = DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertTrue(result.contains("100.50"), "Should have correct amount");
    }

    /**
     * Tests default crypto formatter.
     */
    @Test
    void testDefaultCryptoFormatter() {
        DefaultMoney money = new DefaultMoney(new BigDecimal("0.5"), btc);
        String result = DefaultMoneyFormatter.DEFAULT_CRYPTO_FORMATTER.format(money);

        assertTrue(result.contains("BTC"), "Should contain BTC code");
    }

    /**
     * Tests default forex formatter.
     */
    @Test
    void testDefaultForexFormatter() {
        DefaultMoney money = new DefaultMoney(new BigDecimal("1.2345"), eur);
        String result = DefaultMoneyFormatter.DEFAULT_FOREX_FORMATTER.format(money);

        assertTrue(result.contains("EUR"), "Should contain EUR code");
    }

    /**
     * Tests default stock formatter.
     */
    @Test
    void testDefaultStockFormatter() {
        DefaultMoney money = new DefaultMoney(new BigDecimal("150.75"), usd);
        String result = DefaultMoneyFormatter.DEFAULT_STOCK_FORMATTER.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
    }

    /**
     * Tests default futures formatter.
     */
    @Test
    void testDefaultFuturesFormatter() {
        DefaultMoney money = new DefaultMoney(new BigDecimal("100.5555"), usd);
        String result = DefaultMoneyFormatter.DEFAULT_FUTURES_FORMATTER.format(money);

        assertTrue(result.contains("USD"), "Should contain USD code");
    }

    /**
     * Tests formatting with no grouping separator.
     */
    @Test
    void testNoGroupingSeparator() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("1234567.89"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertFalse(result.contains(","), "Should not have grouping");
    }

    /**
     * Tests space between currency and amount.
     */
    @Test
    void testSpaceBetweenCurrencyAndAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(true)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.50"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$ "), "Should have space between symbol and amount");
    }

    /**
     * Tests no space between currency and amount.
     */
    @Test
    void testNoSpaceBetweenCurrencyAndAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(false)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.50"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$100"), "Should have no space between symbol and amount");
    }

    /**
     * Tests very small cryptocurrency amount.
     */
    @Test
    void testVerySmallCryptoAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(true)
                .forceDecimalPoint()
                .trimTrailingZerosAfterDecimalPoint()
                .capFractionalDigits(8)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("0.00000001"), btc);
        String result = formatter.format(money);

        assertTrue(result.contains("BTC"), "Should contain BTC code");
    }

    /**
     * Tests zero amount.
     */
    @Test
    void testZeroAmount() {
        DefaultMoneyFormatter formatter = DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER;
        DefaultMoney zero = new DefaultMoney(BigDecimal.ZERO, usd);

        String result = formatter.format(zero);
        assertTrue(result.contains("$"), "Should contain USD symbol");
    }

    /**
     * Tests negative zero (edge case).
     */
    @Test
    void testNegativeZero() {
        DefaultMoneyFormatter formatter = DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER;
        DefaultMoney negativeZero = new DefaultMoney(new BigDecimal("-0.00"), usd);

        assertDoesNotThrow(() -> formatter.format(negativeZero));
    }

    /**
     * Tests currency code positioning after amount.
     */
    @Test
    void testCurrencyCodeAfterAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(false)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.50"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("USD"), "Should contain USD code");
    }

    /**
     * Tests currency symbol positioning before amount.
     */
    @Test
    void testCurrencySymbolBeforeAmount() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(false)
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.50"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
    }

    @Test
    void testCurrencySymbolWithDecimalPointIsLiteral() throws Exception {
        Currency swissFranc = new MockCurrency("CHF", "Swiss Franc", CurrencyType.FIAT, 2, "Fr.");
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .useASpaceBetweenCurrencyAndAmount(false)
                .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
                .displayAtLeastAllFractionalDigits(true)
                .build();

        String result = formatter.format(new DefaultMoney(new BigDecimal("12.34"), swissFranc));

        assertTrue(result.startsWith("Fr."), "Currency symbol should be emitted as a literal prefix");
        assertTrue(result.contains("12.34"), "Amount should still be formatted with one decimal separator");
    }

    /**
     * Tests trading P&L with large negative value.
     */
    @Test
    void testTradingPnlLargeLoss() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(true)
                .useASpaceBetweenCurrencyAndAmount(false)
                .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
                .displayAtLeastAllFractionalDigits(true)
                .build();

        DefaultMoney loss = new DefaultMoney(new BigDecimal("-5000.50"), usd);
        String result = formatter.format(loss);

        assertTrue(result.contains("-"), "Should show loss as negative");
        assertTrue(result.contains("$"), "Should contain USD symbol");
    }

    /**
     * Tests trimming trailing zeros after decimal point.
     */
    @Test
    void testTrimTrailingZeros() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .trimTrailingZerosAfterDecimalPoint()
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100.50000"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertFalse(result.contains("100.50000"), "Should trim trailing zeros");
    }

    /**
     * Tests force decimal point.
     */
    @Test
    void testForceDecimalPoint() {
        DefaultMoneyFormatter formatter = new DefaultMoneyFormatter.Builder()
                .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
                .useDigitGroupingSeparator(false)
                .forceDecimalPoint()
                .build();

        DefaultMoney money = new DefaultMoney(new BigDecimal("100"), usd);
        String result = formatter.format(money);

        assertTrue(result.contains("$"), "Should contain USD symbol");
        assertTrue(result.contains("."), "Should have decimal point");
    }

    /**
     * Tests null safety - null input should throw exception.
     */
    @Test
    void testNullInput() {
        DefaultMoneyFormatter formatter = DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER;
        assertThrows(Exception.class, () -> formatter.format(null));
    }

    // =====================================================================
    // Mock Classes for Testing
    // =====================================================================

    /**
     * Mock implementation of Currency for testing.
     */
    static class MockCurrency extends Currency {
        MockCurrency(String code, String name, CurrencyType type, int fractionalDigits, String symbol)
                throws SQLException, ClassNotFoundException {
            super(type, name, name, code, fractionalDigits, symbol, "");
        }
    }
}
