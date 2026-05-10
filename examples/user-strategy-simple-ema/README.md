# Simple EMA Crossover User Strategy

A complete example of a user-developed custom trading strategy for the InvestPro platform.

## Overview

This example demonstrates how to create your own trading strategy using the InvestPro **UserStrategy API**. The strategy implements a simple Exponential Moving Average (EMA) crossover pattern:

- **BUY Signal**: When the fast EMA (12 periods) crosses above the slow EMA (26 periods)
- **SELL Signal**: When the fast EMA crosses below the slow EMA
- **HOLD**: When there's no crossover or insufficient data

## Building the Strategy

### Prerequisites

- Java 21+ (Eclipse Temurin or OpenJDK)
- Maven 3.8+
- InvestPro 1.0.0+ (installed or available in your local Maven repository)

### Build Instructions

```bash
# Clean and build the JAR
mvn clean package

# The compiled JAR will be created at:
# target/simple-ema-user-strategy-1.0.0.jar
```

### Creating a Fat JAR (with all dependencies)

If your strategy has external dependencies, create a fat JAR:

```bash
mvn clean assembly:single
```

## Deploying the Strategy

### Step 1: Create strategies/ Directory

In your InvestPro installation directory, create a `strategies/` folder:

```
InvestPro/
├── investpro.jar
├── strategies/          <-- Create this folder
├── trades.properties
└── ...
```

### Step 2: Copy the JAR

Copy the compiled JAR to the strategies directory:

```bash
cp target/simple-ema-user-strategy-1.0.0.jar /path/to/InvestPro/strategies/
```

### Step 3: Restart InvestPro

The next time InvestPro starts:

1. **StrategyBootstrapper** discovers your JAR via ServiceLoader
2. **UserStrategyLoader** scans the `strategies/` directory
3. Your strategy is validated and registered
4. It appears in:
   - Strategy Combo Box (with `[User]` prefix)
   - Backtesting Panel
   - Strategy Developer Panel

## Using the Strategy

### In Backtesting

1. Open the **Backtesting Panel**
2. Select **[User] simple-ema-crossover** from the strategy dropdown
3. Configure TradePair, Timeframe, Date Range, Initial Balance
4. Click **Run Backtest**

### In Strategy Developer Panel

1. Open the **Strategy Developer Panel**
2. View your loaded strategy in the table
3. Use buttons to:
   - **Validate Selected**: Verify strategy configuration
   - **Backtest Selected**: Run backtest immediately
   - **Reload Strategies**: Discover newly added JARs
   - **Open Folder**: Browse `strategies/` directory

## Code Structure

### Main Class

`src/main/java/com/investpro/examples/strategy/SimpleEmaUserStrategy.java`

Implements the `UserStrategy` interface with:

```java
public interface UserStrategy {
    String getId();                              // Unique ID
    String getName();                            // Display name
    String getDescription();                     // Strategy explanation
    int requiredWarmupBars();                   // Minimum candles needed
    StrategySignal generateSignal(StrategyContext context);  // Main logic
}
```

### ServiceLoader Registration

`src/main/resources/META-INF/services/org.investpro.strategy.api.UserStrategy`

Lists the implementation class:

```
com.investpro.examples.strategy.SimpleEmaUserStrategy
```

This file enables automatic discovery via Java's ServiceLoader.

## Key Implementation Details

### 1. Safe Data Access

The strategy is **read-only** and only accesses:
- Market candle data (OHLCV)
- Current price
- Bid/Ask spreads
- Trading session status

**NOT accessible** (for safety):
- Order placement
- Account modifications
- Exchange connection
- API credentials
- Risk management rules

### 2. Error Handling

All user code is wrapped in try-catch blocks. If an exception occurs:

```java
catch (Exception e) {
    return StrategySignal.hold(..., "Error: " + e.getMessage());
}
```

The strategy gracefully returns a HOLD signal instead of crashing.

### 3. Null Safety

Every optional field is checked before use:

```java
context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN"
```

### 4. Signal Generation

Use `StrategySignal.Builder` for complex signals:

```java
return StrategySignal.builder()
    .symbol(...)
    .timeframe(...)
    .side(StrategySignal.Side.BUY)
    .confidence(0.65)         // 0-1 scale
    .amount(0.5)              // Position size
    .reason("Custom reason")
    .generatingStrategy(getId())
    .build();
```

## Validation Rules

When your strategy is loaded, InvestPro validates:

1. **ID**: Non-blank, alphanumeric + hyphens/underscores, max 128 chars
2. **Name**: Non-blank, max 200 chars
3. **Warmup Bars**: Greater than 0, warning if <10 or >5000
4. **Signal Generation**: Successfully returns a non-null StrategySignal
5. **Signal Normalization**: Signal can be normalized without errors

If validation fails, the strategy is marked INVALID and won't be assigned for live trading.

## Safety Guarantees

### What This Strategy CAN Do

✅ Read market data (candles, price, bid/ask)  
✅ Access trading session status  
✅ Generate signals based on technical analysis  
✅ Log information for debugging  
✅ Perform mathematical calculations  
✅ Use standard Java libraries  

### What This Strategy CANNOT Do

❌ Place orders directly  
❌ Access exchange API  
❌ Modify account balance  
❌ Read API credentials  
❌ Bypass risk management rules  
❌ Access other strategies' state  
❌ Modify platform configuration  

Attempting any of these will result in a HOLD signal and error logging.

## Testing Your Strategy

### Unit Testing

Create unit tests for your signal logic:

```java
@Test
public void testBuyCrossover() {
    SimpleEmaUserStrategy strategy = new SimpleEmaUserStrategy();
    List<CandleData> candles = createTestCandles(30);
    
    StrategyContext context = StrategyContext.builder()
        .candles(candles)
        .symbol(new TradePair("BTC", "USD"))
        .timeframe(Timeframe.ONE_HOUR)
        .build();
    
    StrategySignal signal = strategy.generateSignal(context);
    
    assertEquals(StrategySignal.Side.BUY, signal.getSide());
}
```

### Manual Backtesting

1. Deploy to `strategies/` directory
2. Restart InvestPro
3. Open Strategy Developer Panel
4. Click "Backtest Selected"
5. Review equity curve and metrics

### Paper Trading

After backtesting, enable paper trading:

1. Assign strategy in Strategy Lab
2. Run in paper trading mode
3. Monitor P&L without real money at risk
4. Review results before live assignment

## Customization

### Modifying EMA Periods

Change the constants at the top of the class:

```java
private static final int FAST_PERIOD = 12;   // Change to 10, 15, etc.
private static final int SLOW_PERIOD = 26;   // Change to 20, 30, etc.
```

### Different Signal Logic

Replace the signal generation in `generateSignal()`:

```java
// Your custom logic here
if (myCondition) {
    return StrategySignal.buy(...);
} else {
    return StrategySignal.sell(...);
}
```

### Adding Parameters

Create a configuration class and read from properties file:

```java
private final double thresholdPercent = 0.02; // 2% threshold
```

## Troubleshooting

### Strategy Not Loading

**Problem**: Strategy doesn't appear in the dropdown after restart.

**Solution**:
1. Check logs for errors: `grep "simple-ema" logs/*.log`
2. Verify JAR is in `strategies/` directory
3. Check ServiceLoader file exists: `jar tf simple-ema-user-strategy-1.0.0.jar | grep services`
4. Ensure class name matches in `META-INF/services/` file

### Validation Failures

**Problem**: Strategy marked as INVALID in Strategy Developer Panel.

**Solution**:
1. Click "Validate Selected" for detailed error message
2. Check logs for `UserStrategyValidator` output
3. Ensure `getId()`, `getName()`, and `generateSignal()` work correctly
4. Test with minimum data: `requiredWarmupBars()` candles

### Backtest Crashes

**Problem**: Backtest fails with an exception.

**Solution**:
1. Check `generateSignal()` for null pointer exceptions
2. Verify all list accesses are bounds-checked
3. Add defensive coding: `if (candles == null || candles.isEmpty()) return HOLD`
4. Test with different timeframes and symbols

## Packaging Tips

### Minimize JAR Size

If your JAR is large, check for unnecessary dependencies:

```bash
jar tf simple-ema-user-strategy-1.0.0.jar | wc -l
```

Keep it under 1 MB for fast loading.

### Multiple Strategies in One JAR

You can package multiple strategies in one JAR:

```
src/main/java/
├── com/investpro/examples/strategy/
│   ├── SimpleEmaUserStrategy.java
│   ├── RsiUserStrategy.java
│   └── MacdUserStrategy.java

src/main/resources/
├── META-INF/services/
│   └── org.investpro.strategy.api.UserStrategy
       ├── com.investpro.examples.strategy.SimpleEmaUserStrategy
       ├── com.investpro.examples.strategy.RsiUserStrategy
       └── com.investpro.examples.strategy.MacdUserStrategy
```

## Next Steps

1. **Customize**: Modify the EMA periods or signal logic
2. **Test**: Build and deploy to test backtesting
3. **Enhance**: Add indicators (RSI, MACD, Bollinger Bands)
4. **Validate**: Check validation results in Strategy Developer Panel
5. **Backtest**: Run extensive backtests with historical data
6. **Paper Trade**: Assign for paper trading to validate live behavior
7. **Deploy**: Once confident, assign for live trading

## Support

For issues or questions:
1. Check InvestPro logs in `logs/` directory
2. Review the `UserStrategy` API documentation
3. Inspect the `SimpleEmaUserStrategy` source code
4. Test in backtesting before live assignment

## License

This example code is provided as-is for educational purposes. Modify and distribute freely within the InvestPro ecosystem.
