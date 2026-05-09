# InvestPro User Strategy Development Guide

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [API Reference](#api-reference)
4. [Creating Your Strategy](#creating-your-strategy)
5. [Packaging and Deployment](#packaging-and-deployment)
6. [Validation](#validation)
7. [Backtesting](#backtesting)
8. [Paper Trading](#paper-trading)
9. [Live Assignment](#live-assignment)
10. [Safety Guarantees](#safety-guarantees)
11. [FAQ](#faq)

## Overview

The **User Strategy System** allows you to develop custom trading strategies for InvestPro without modifying the platform code. Your strategies are:

- **Isolated**: Run in a controlled environment with no access to dangerous APIs
- **Validated**: Automatically checked before backtesting or live assignment
- **Observable**: Full logging and performance metrics in the Strategy Developer Panel
- **Testable**: Complete backtesting and paper trading before live deployment
- **Discoverable**: Automatically loaded from the `strategies/` directory using Java's ServiceLoader pattern

### The Platform Principle

> **"Custom strategies are allowed to think. Only the platform is allowed to execute."**

This means:
- ✅ You develop sophisticated signal logic
- ✅ You analyze data and patterns
- ✅ ❌ You do NOT place orders, modify accounts, or access credentials
- ✅ The platform handles all order execution with safety rules

## Getting Started

### 1. Set Up Your Development Environment

**Requirements**:
- Java 21+ (Eclipse Temurin or OpenJDK)
- Maven 3.8+ (Apache Maven)
- IDE: IntelliJ IDEA, VS Code, or Eclipse

**Install Java 21**:

```bash
# macOS/Linux with Homebrew
brew install openjdk@21

# Windows - Download from:
# https://www.eclipse.org/downloads/packages/release/temurin-21

# Verify installation
java --version
# openjdk 21.x.x
```

**Install Maven**:

```bash
# macOS/Linux with Homebrew
brew install maven

# Verify installation
mvn --version
# Apache Maven 3.9.x
```

### 2. Clone or Reference the Example Project

Start with the provided example:

```bash
cd your-investpro-directory/examples/user-strategy-simple-ema
mvn clean package
```

This creates: `target/simple-ema-user-strategy-1.0.0.jar`

### 3. Create Your Own Project

Copy the example and customize:

```bash
cp -r examples/user-strategy-simple-ema my-custom-strategy
cd my-custom-strategy

# Modify pom.xml
# - Change artifactId
# - Change version if needed
# - Update groupId if desired

# Create your strategy class in src/main/java/
# Update ServiceLoader file with your class name
```

## API Reference

### UserStrategy Interface

```java
public interface UserStrategy {
    
    /**
     * Unique identifier for this strategy.
     * Must be alphanumeric + hyphens/underscores, max 128 chars.
     * Examples: "ema-crossover", "rsi-oversold", "macd-divergence"
     */
    @NotNull String getId();
    
    /**
     * Human-readable strategy name for UI display.
     * Max 200 characters.
     */
    @NotNull String getName();
    
    /**
     * Detailed description of the strategy logic.
     * Optional - default returns empty string.
     */
    @NotNull default String getDescription() {
        return "";
    }
    
    /**
     * Minimum number of historical candles needed before
     * the strategy can generate reliable signals.
     * 
     * Defaults to 100. Set higher if your indicators need
     * longer warmup periods (e.g., 200-period SMA needs 200).
     */
    default int requiredWarmupBars() {
        return 100;
    }
    
    /**
     * Generate a trading signal based on current market data.
     * 
     * Called every bar/candle for live trading or backtesting.
     * Must return a non-null StrategySignal.
     * 
     * Safe API - you can only READ data, never EXECUTE trades.
     */
    @NotNull StrategySignal generateSignal(@NotNull StrategyContext context);
}
```

### StrategyContext

Available data for your strategy analysis:

```java
public class StrategyContext {
    
    /**
     * Historical candles for this symbol/timeframe.
     * First element is oldest, last element is newest.
     */
    List<CandleData> getCandles();
    
    /**
     * Current price (most recent close or mark price).
     */
    double getCurrentPrice();
    
    /**
     * Trading pair info (e.g., BTC/USD, ETH/USDT).
     */
    TradePair getSymbol();
    
    /**
     * Timeframe of the data (1M, 5M, 15M, 1H, 4H, 1D, etc.).
     */
    Timeframe getTimeframe();
    
    /**
     * Current bid/ask spread if available from exchange.
     */
    double getBidPrice();
    double getAskPrice();
    
    /**
     * Historical volatility estimate for this period.
     */
    double getVolatility();
    
    /**
     * Whether market is currently open for this pair.
     */
    boolean isTradableNow();
}
```

### StrategySignal

Your strategy generates signals to be executed:

```java
public class StrategySignal {
    
    enum Side { BUY, SELL, HOLD }
    
    // Factory methods for simple signals
    static StrategySignal buy(...);
    static StrategySignal sell(...);
    static StrategySignal hold(...);
    
    // Builder for complex signals
    static Builder builder()
        .symbol(String)
        .timeframe(String)
        .side(Side)
        .confidence(0.0 - 1.0)      // How confident you are (0.5 = 50%)
        .amount(0.0 - 1.0)          // Position size (0.5 = 50% of account)
        .entryPrice(double)         // Expected entry price
        .stopLoss(double)           // Maximum loss threshold
        .profitTarget(double)       // Target profit level
        .reason(String)             // Why you generated this signal
        .generatingStrategy(String) // Your strategy ID
        .build();
}
```

### CandleData

Individual OHLCV candle:

```java
public class CandleData {
    
    long getTimestamp();            // Unix timestamp in milliseconds
    double getOpen();
    double getHigh();
    double getLow();
    double getClose();
    double getVolume();             // In base currency or contract units
    String getSymbol();
}
```

## Creating Your Strategy

### Step 1: Implement UserStrategy

```java
package com.example.strategy;

import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.api.UserStrategy;
import org.jetbrains.annotations.NotNull;

public class MyCustomStrategy implements UserStrategy {
    
    @Override
    public @NotNull String getId() {
        return "my-custom-strategy";
    }
    
    @Override
    public @NotNull String getName() {
        return "My Custom Trading Strategy";
    }
    
    @Override
    public @NotNull String getDescription() {
        return "A custom strategy combining multiple indicators.";
    }
    
    @Override
    public int requiredWarmupBars() {
        return 200;  // Requires 200 bars of history
    }
    
    @Override
    public @NotNull StrategySignal generateSignal(@NotNull StrategyContext context) {
        try {
            // Your custom logic here
            
            if (shouldBuy(context)) {
                return StrategySignal.buy(
                    context.getSymbol().toString(),
                    context.getTimeframe().toString(),
                    getId(),
                    "Custom buy condition met");
            }
            
            if (shouldSell(context)) {
                return StrategySignal.sell(...);
            }
            
            return StrategySignal.hold(...);
            
        } catch (Exception e) {
            // Always handle errors gracefully
            return StrategySignal.hold(
                context.getSymbol().toString(),
                context.getTimeframe().toString(),
                getId(),
                "Error: " + e.getMessage());
        }
    }
    
    private boolean shouldBuy(StrategyContext context) {
        // Your buy logic
        return false;
    }
    
    private boolean shouldSell(StrategyContext context) {
        // Your sell logic
        return false;
    }
}
```

### Step 2: Best Practices

**1. Always Check for Null Values**

```java
// WRONG ❌
double price = context.getCurrentPrice();

// RIGHT ✅
double price = context.getCurrentPrice();
if (Double.isNaN(price) || price <= 0) {
    return StrategySignal.hold(..., "Invalid price data");
}
```

**2. Verify Sufficient Data**

```java
List<CandleData> candles = context.getCandles();
if (candles == null || candles.size() < requiredWarmupBars()) {
    return StrategySignal.hold(..., "Insufficient data");
}
```

**3. Handle Edge Cases**

```java
// Check for NaN in calculations
double ema = calculateEma(candles, 12);
if (Double.isNaN(ema)) {
    return StrategySignal.hold(..., "Calculation error");
}

// Avoid division by zero
double ratio = numerator / Math.max(denominator, 0.0001);

// Check list boundaries
if (index < 0 || index >= list.size()) {
    return StrategySignal.hold(..., "Index out of bounds");
}
```

**4. Log Important Information**

```java
// User strategies can access SLF4J for logging
private static final Logger log = LoggerFactory.getLogger(MyStrategy.class);

log.debug("Calculated EMA: {}", ema);
log.warn("Insufficient volume detected");
log.error("Critical error in signal generation", exception);
```

**5. Avoid Blocking Operations**

❌ Do NOT use:
- `Thread.sleep()`
- Network requests
- File I/O
- Database queries

✅ Only use:
- Math operations
- Loops and conditionals
- String operations
- Standard Java libraries

## Packaging and Deployment

### Building Your JAR

```bash
mvn clean package
```

This creates: `target/my-custom-strategy-1.0.0.jar`

### ServiceLoader Registration

Create file: `src/main/resources/META-INF/services/org.investpro.strategy.api.UserStrategy`

Content (one class per line):

```
com.example.strategy.MyCustomStrategy
com.example.strategy.AnotherStrategy
```

InvestPro uses this file to automatically discover your strategies.

### Deploying to InvestPro

1. **Locate your InvestPro directory**:
   ```
   /path/to/InvestPro/
   ├── investpro.jar
   ├── config.properties
   └── strategies/              ← Create this folder
   ```

2. **Copy your JAR**:
   ```bash
   cp target/my-custom-strategy-1.0.0.jar /path/to/InvestPro/strategies/
   ```

3. **Restart InvestPro**:
   - The next startup automatically loads your strategy
   - Logs will show:
     ```
     [INFO] User strategy loading initiated...
     [INFO] Successfully loaded my-custom-strategy from my-custom-strategy-1.0.0.jar
   ```

### Multiple Strategies in One JAR

You can package multiple strategies:

```java
// src/main/java/com/example/strategy/
├── EmaStrategy.java
├── RsiStrategy.java
└── MacdStrategy.java
```

Register all in ServiceLoader:

```
com.example.strategy.EmaStrategy
com.example.strategy.RsiStrategy
com.example.strategy.MacdStrategy
```

When loaded, all three strategies are available.

## Validation

### Automatic Validation

When InvestPro loads your strategy, it automatically validates:

| Check | Requirement | Impact |
|-------|-------------|--------|
| **ID** | Alphanumeric + hyphens/underscores, max 128 chars | Strategy won't load if invalid |
| **Name** | Non-blank, max 200 chars | Strategy won't load if invalid |
| **Warmup** | `requiredWarmupBars() > 0` | Warning if < 10 or > 5000 |
| **Signal** | `generateSignal()` returns non-null StrategySignal | Strategy marked INVALID |
| **Normalization** | Signal can be normalized (0-1 scales) | Strategy marked INVALID |

### Manual Validation in UI

Open **Strategy Developer Panel** → Select your strategy → Click **Validate Selected**

Results show:
- ✅ All checks passed → Strategy is VALID
- ❌ One or more failed → Detailed error message

### Validation Failures

**Problem**: "ID contains invalid characters"
- **Cause**: ID has spaces, symbols, or special chars
- **Fix**: Use only: a-z, A-Z, 0-9, hyphens (-), underscores (_)

**Problem**: "Signal generation returned null"
- **Cause**: Your `generateSignal()` method returned `null`
- **Fix**: Always return a `StrategySignal` (use `StrategySignal.hold()` as fallback)

**Problem**: "requireWarmupBars returned invalid value"
- **Cause**: Your method returned 0 or negative number
- **Fix**: Return at least 1 (typically 50-200 for most strategies)

## Backtesting

### Running a Backtest

1. Open **Backtesting Panel**
2. Select your strategy from dropdown (appears as `[User] my-custom-strategy`)
3. Configure:
   - **Symbol/Pair**: BTC/USD, ETH/USDT, etc.
   - **Timeframe**: 1M, 5M, 15M, 1H, 4H, 1D
   - **Date Range**: Start and end dates for historical data
   - **Initial Balance**: Starting account balance ($, €, etc.)
   - **Order Type**: MARKET, LIMIT, STOP_LIMIT

4. Click **Run Backtest**

### Interpreting Results

The backtest generates metrics:

| Metric | Meaning |
|--------|---------|
| **Total Return %** | Overall profit/loss as percentage |
| **Win Rate** | % of profitable trades |
| **Max Drawdown** | Largest peak-to-trough decline |
| **Sharpe Ratio** | Risk-adjusted return (higher is better) |
| **Profit Factor** | Gross profit / Gross loss |

### Example Results

```
Backtest Results: BTC/USD, 1H, 2024-01-01 to 2024-03-31

Total Return:        +15.3%
Win Rate:            58.2% (45 wins, 32 losses)
Max Drawdown:        -8.5%
Sharpe Ratio:        1.24
Profit Factor:       2.1x

Equity Curve:        [Line chart showing growth]
```

### What Backtest Tests

✅ Signal generation logic  
✅ Entry and exit prices  
✅ Trade frequency  
✅ Overall profitability  
✅ Drawdown behavior  

### What Backtest Does NOT Test

❌ Slippage (actual execution may differ from signal price)  
❌ Spread impact (bid-ask cost)  
❌ Commissions and fees  
❌ Liquidity constraints  
❌ Real-time execution delays  

**For more realistic results**, assume:
- 0.1% slippage on entries
- 0.05% spread cost on exits
- 0.1% trading commission

## Paper Trading

After backtesting shows promise, test in **paper trading** (live data, no real money):

### Enable Paper Trading

1. Open **Strategy Lab**
2. Select your strategy
3. Click **Assign for Paper Trading**
4. Monitor P&L for 1-2 weeks
5. Review all generated trades

### What Paper Trading Tests

✅ Real market data (not historical)  
✅ Live signal generation  
✅ Execution timing  
✅ Slippage and spreads  
✅ Commission impact  
✅ Market behavior changes  

### Paper Trading vs Live Trading

| Feature | Paper | Live |
|---------|-------|------|
| Real money | ❌ No | ✅ Yes |
| Real market data | ✅ Yes | ✅ Yes |
| Realistic execution | ~80% | 100% |
| Risk | 🟢 None | 🔴 Real |
| Ideal duration | 1-4 weeks | On-going |

### Sample Paper Trading Period

```
Strategy: my-custom-strategy
Period: 2024-04-01 to 2024-04-14 (2 weeks)

Trade Signals: 23
Executed Trades: 22 (1 missed due to liquidity)
Win Rate: 54.5% (12 wins, 10 losses)
Paper P&L: +$2,340 (2.3%)
Real P&L estimate: +1.8% (accounting for slippage)

Assessment: ✅ READY FOR LIVE TRADING
```

## Live Assignment

After validation + backtesting + paper trading, assign to live trading:

### Safety Gates Before Live Assignment

Your strategy must meet ALL of these before live trading:

```
✅ Status: LOADED               (no loading errors)
✅ Validated: YES               (all checks passed)
✅ Backtest: PASSED             (positive return)
✅ Paper Trading: PASSED        (2+ weeks, positive)
✅ Score: >= 0.65               (configurable threshold)
✅ Risk Rules: APPROVED         (risk management allows)
✅ Session: TRADABLE            (market is open)
```

If ANY gate fails, you get a clear error:

```
❌ Cannot assign strategy my-custom-strategy

Reason: Paper trading not completed
Status: VALIDATED but not paper-tested
Action: Complete 2 weeks of paper trading first
```

### Assigning for Live Trading

1. Open **Strategy Lab**
2. Select your strategy
3. Verify all safety gates: ✅
4. Click **Assign for Live Trading**
5. Confirm: "Yes, assign this strategy"
6. Strategy is now active for trading

### Monitoring Live Performance

Once live:

1. View in **Strategy Developer Panel**:
   - Status: `LIVE_ASSIGNED`
   - Real P&L tracking
   - Trade history

2. Daily reviews:
   - Compare to backtest expectations
   - Check for behavioral changes
   - Monitor win rate and max drawdown

3. Quick disable if needed:
   - Right-click → **Disable Strategy**
   - Immediately stops generating new signals
   - Existing positions are managed by risk rules

## Safety Guarantees

### What Your Strategy CAN Access

✅ **Market Data**:
- Historical candles (OHLCV)
- Current price and bid/ask
- Volume and volatility
- Trading session status

✅ **Calculations**:
- Math operations
- Indicator calculations
- Data transformations
- Time analysis

✅ **Control**:
- Signal generation
- Signal parameters (confidence, amount, target, stop)
- Logging and diagnostics

### What Your Strategy CANNOT Access

❌ **Order Execution**:
- No `placeOrder()` method
- No `executeMarketOrder()` function
- No direct exchange connection

❌ **Account Information**:
- No `getBalance()` function
- No position data
- No account equity access

❌ **Credentials**:
- No API keys
- No secret keys
- No authentication tokens

❌ **Platform Modification**:
- No config changes
- No strategy registry modification
- No risk rule bypassing

### Enforcement

If your code tries unsafe operations:

1. **Compile Time**: IDE shows errors
   ```
   Cannot resolve symbol 'placeOrder'
   Cannot access package 'org.investpro.core'
   ```

2. **Runtime**: Graceful fallback
   ```
   Signal: HOLD
   Reason: "Attempted unsafe operation X"
   ```

3. **Validation**: Strategy marked INVALID
   ```
   Status: INVALID
   Error: "Strategy violated safety constraints"
   ```

## FAQ

### Q: My strategy isn't appearing in the dropdown. What's wrong?

**A**: Check these in order:

1. **Is the JAR in `strategies/` folder?**
   ```bash
   ls /path/to/InvestPro/strategies/
   ```

2. **Did you restart InvestPro?**
   - Log out and back in, or restart the application

3. **Is the ServiceLoader file correct?**
   ```bash
   jar tf my-strategy-1.0.0.jar | grep services
   # Should show: META-INF/services/org.investpro.strategy.api.UserStrategy
   ```

4. **Check logs for errors**:
   ```bash
   tail -50 logs/investpro.log | grep "my-strategy"
   ```

### Q: Why is my strategy INVALID?

**A**: Click "Validate Selected" in Strategy Developer Panel for specific error. Common causes:

1. `generateSignal()` returns null - Always return a StrategySignal
2. Invalid ID format - Use only alphanumeric + hyphens/underscores
3. Exception in signal generation - Add try-catch blocks
4. NaN/Inf in calculations - Check for division by zero

### Q: Can I have multiple strategies in one JAR?

**A**: Yes! List all in ServiceLoader file:

```
com.example.strategy.Strategy1
com.example.strategy.Strategy2
com.example.strategy.Strategy3
```

All three will load independently.

### Q: What's a good warmup period?

**A**: Depends on your longest indicator:

- Simple strategies (2-3 indicators): 50-100 bars
- Complex strategies (5+ indicators): 100-200 bars
- Machine learning models: 200-500 bars

Set conservatively - more is safer than too little.

### Q: Can I use external libraries?

**A**: Yes, but they must be in your JAR:

```bash
mvn assembly:single
```

This creates a "fat JAR" with all dependencies included.

### Q: How often does my strategy run?

**A**: Every bar of your selected timeframe:

- 1M timeframe: Every minute
- 5M timeframe: Every 5 minutes
- 1H timeframe: Every hour
- 1D timeframe: Every day

### Q: What happens if my strategy crashes?

**A**: The platform automatically:

1. Catches the exception
2. Logs the error
3. Returns `HOLD` signal
4. Continues operation
5. Marks strategy as having issues in logs

Your crash NEVER takes down the platform.

### Q: Can I update my strategy?

**A**: Yes:

1. **Stop** the strategy (Disable in UI)
2. **Rebuild** and repackage
3. **Copy** new JAR to `strategies/` folder
4. **Restart** InvestPro or click "Reload Strategies"
5. **Validate** and **Re-enable**

For zero-downtime updates, use new JAR filename (e.g., v1.1.0).

### Q: Where's my strategy's trading activity?

**A**: 

- **Backtesting**: Results in Backtest window
- **Paper Trading**: View in "Strategy Details" tab
- **Live Trading**: View in Strategy Lab performance metrics

All trades are logged with timestamp, entry, exit, and P&L.

## Conclusion

You now have everything needed to develop, test, and deploy custom trading strategies in InvestPro. The safety guarantees ensure your custom code never jeopardizes the platform, while the complete workflow (validate → backtest → paper trade → live) minimizes risk.

**Happy strategy developing!** 🚀
