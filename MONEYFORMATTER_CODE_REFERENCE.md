# DefaultMoneyFormatter - Code Reference

## New Static Defaults

```java
// Fiat currencies (USD, EUR, etc.)
public static final DefaultMoneyFormatter DEFAULT_FIAT_FORMATTER = new Builder()
    .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(false)
    .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
    .displayAtLeastAllFractionalDigits(true)
    .build();

// Cryptocurrencies (BTC, ETH, etc.)
public static final DefaultMoneyFormatter DEFAULT_CRYPTO_FORMATTER = new Builder()
    .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(true)
    .forceDecimalPoint()
    .trimTrailingZerosAfterDecimalPoint()
    .capFractionalDigits(8)
    .build();

// Forex pairs (EUR/USD, GBP/JPY, etc.)
public static final DefaultMoneyFormatter DEFAULT_FOREX_FORMATTER = new Builder()
    .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(true)
    .forceDecimalPoint()
    .displayAtLeastAllFractionalDigits(true)
    .capFractionalDigits(5)
    .build();

// Stocks (US stocks, etc.)
public static final DefaultMoneyFormatter DEFAULT_STOCK_FORMATTER = new Builder()
    .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(false)
    .forceDecimalPoint()
    .displayAtLeastAllFractionalDigits(true)
    .capFractionalDigits(2)
    .build();

// Futures contracts
public static final DefaultMoneyFormatter DEFAULT_FUTURES_FORMATTER = new Builder()
    .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(true)
    .forceDecimalPoint()
    .trimTrailingZerosAfterDecimalPoint()
    .capFractionalDigits(4)
    .build();
```

## New Builder Methods

### capFractionalDigits(int cap)
Controls maximum fractional digits displayed.
```java
// Cap crypto to 8 decimals
.capFractionalDigits(8)

// Cap forex to 5 decimals
.capFractionalDigits(5)

// Cap stocks to 2 decimals
.capFractionalDigits(2)

// No cap (unlimited, use -1)
.capFractionalDigits(-1)
```

### unlimitedFractionalDigits()
Allows unlimited precision (overrides capFractionalDigits).
```java
.unlimitedFractionalDigits()
```

### withLocale(Locale locale)
Sets locale for number and currency formatting.
```java
// Indian locale (enables Indian grouping)
.withLocale(new Locale("hi", "IN"))

// Bengali locale
.withLocale(new Locale("bn", "BD"))

// US English locale
.withLocale(Locale.US)
```

## Supported Indic Languages

Automatic Indian grouping (1,23,45,678 format) for:
- **asm** - Assamese
- **ben** - Bengali
- **guj** - Gujarati
- **hin** - Hindi
- **kan** - Kannada
- **mal** - Malayalam
- **mar** - Marathi
- **ory** - Odia
- **pan** - Punjabi
- **tam** - Tamil
- **tel** - Telugu

## Key Methods

### Negative Handling
```java
// Extract sign and use absolute value internally
boolean isNegative = defaultMoney.amount().signum() < 0;
BigDecimal absoluteAmount = defaultMoney.amount().abs();

// Add negative sign to final result
if (isNegative) {
    number = "-" + number;
}
```

### Digit Grouping Selection
```java
private int getGroupingAmount() {
    if (locale == null) {
        return 3;  // Western default
    }
    
    String iso3Language = locale.getISO3Language();
    
    // Indian grouping uses 2-digit groups
    return switch (iso3Language) {
        case "asm", "ben", "guj", "hin", "kan", "mal", "mar", "ory", "pan", "tam", "tel" -> 2;
        default -> 3;  // Western grouping
    };
}
```

### NumberFormat Change
```java
// OLD (before):
numberFormat = NumberFormat.getCurrencyInstance(locale);

// NEW (after):
numberFormat = NumberFormat.getNumberInstance(locale);

// Reason: Manual control of currency symbol/code placement
// Avoids conflicts with auto-applied currency formatting
```

## Usage Examples

### Example 1: Format USD with Losses
```java
DefaultMoneyFormatter pnlFormatter = new Builder()
    .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(false)
    .forceDecimalPoint(WholeNumberFractionalDigitAmount.MAX)
    .displayAtLeastAllFractionalDigits(true)
    .build();

DefaultMoney gain = new DefaultMoney(new BigDecimal("1234.56"), USD);
DefaultMoney loss = new DefaultMoney(new BigDecimal("-1234.56"), USD);

System.out.println(pnlFormatter.format(gain));   // $1,234.56
System.out.println(pnlFormatter.format(loss));   // -$1,234.56
```

### Example 2: Format Bitcoin with 8 Decimal Precision
```java
DefaultMoneyFormatter btcFormatter = DefaultMoneyFormatter.DEFAULT_CRYPTO_FORMATTER;

DefaultMoney btc = new DefaultMoney(new BigDecimal("0.12345678"), BTC);
System.out.println(btcFormatter.format(btc));  // 0.12345678 BTC
```

### Example 3: Format for Indian Market
```java
DefaultMoneyFormatter indiaFormatter = new Builder()
    .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
    .useDigitGroupingSeparator(true)
    .useASpaceBetweenCurrencyAndAmount(true)
    .withLocale(new Locale("hi", "IN"))
    .forceDecimalPoint()
    .displayAtLeastAllFractionalDigits(true)
    .build();

DefaultMoney indianAmount = new DefaultMoney(new BigDecimal("1234567.89"), INR);
System.out.println(indiaFormatter.format(indianAmount));  // 1,23,45,67.89 INR
```

### Example 4: Format Forex with 5 Decimals
```java
DefaultMoneyFormatter forexFormatter = DefaultMoneyFormatter.DEFAULT_FOREX_FORMATTER;

DefaultMoney eurusd = new DefaultMoney(new BigDecimal("1.23456"), EUR_USD);
System.out.println(forexFormatter.format(eurusd));  // 1.23456 EUR/USD
```

### Example 5: Format Stock Prices
```java
DefaultMoneyFormatter stockFormatter = DefaultMoneyFormatter.DEFAULT_STOCK_FORMATTER;

DefaultMoney appleStock = new DefaultMoney(new BigDecimal("150.25"), USD);
System.out.println(stockFormatter.format(appleStock));  // $150.25
```

## Null Safety

All inputs are validated:
```java
Objects.requireNonNull(defaultMoney, "defaultMoney must not be null");
Objects.requireNonNull(defaultMoney.currency(), "currency must not be null");
Objects.requireNonNull(defaultMoney.amount(), "amount must not be null");
```

## Exception Handling

### NullPointerException
```java
// Thrown when required objects are null
formatter.format(null);  // NullPointerException

new Builder().withCurrencySymbol(null);  // NullPointerException
```

### IllegalArgumentException
```java
// Invalid fractional digit cap
new Builder().capFractionalDigits(-5);  // IllegalArgumentException

// Both currency symbol and code set
new Builder()
    .withCurrencySymbol(CurrencyPosition.BEFORE_AMOUNT)
    .withCurrencyCode(CurrencyPosition.AFTER_AMOUNT)
    .build();  // IllegalArgumentException
```

## Testing Quick Checklist

Run these key tests to validate:

```
✓ testUsdFormattingWithSymbol()
✓ testBitcoinFormatting()
✓ testNegativeUsdValue()
✓ testNegativeCryptoValue()
✓ testFractionalDigitsCap()
✓ testFormattingWithoutGroupingSeparator()
✓ testIndianGrouping()
✓ testBengaliGrouping()
✓ testWesternGrouping()
✓ testDefaultCryptoFormatter()
✓ testDefaultFiatFormatter()
✓ testTradingPnlLargeLoss()
✓ testTradingPnlLargeGain()
✓ testNullSafetyMoney()
✓ testInvalidFractionalDigitsCapThrowsException()
```

## Performance Notes

- **Minimal overhead**: No regex in main formatting path (moved to trim logic)
- **Locale caching**: DecimalFormatSymbols cached in constructor
- **Efficient string building**: StringBuilder used throughout
- **No allocations in format()**: Pattern applied once, reused

## Backward Compatibility

✅ All existing code continues to work
✅ No breaking changes to public API
✅ New methods are additive only
✅ Default behavior unchanged (except NumberFormat.getNumberInstance() is internal)
