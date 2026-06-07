# Java Strategy Architecture - Implementation Guide

## Overview

A comprehensive catalog-driven strategy architecture for the InvestPro trading platform, inspired by Python strategy patterns but implemented as a unified Java engine. Instead of creating hundreds of individual strategy classes, this architecture uses:

- **StrategyCatalog**: Central registry with 17 core strategies and 1000+ variants
- **UnifiedStrategy**: Single configurable engine implementing all strategy logic
- **FeaturePipeline**: Technical indicator computation with lookahead bias prevention
- **StrategyParameters**: Modular parameter profiles for variants

## Architecture

### Core Components

#### Auto Strategy Lab (`org.investpro.strategy.auto`)
Auto Strategy Lab builds on the normal `StrategyDefinition` model. It can generate
rule-based candidates, mutate existing strategies, evaluate them with
`StrategyBacktestRunner`, rank the results, and write assignment decisions to the
strategy assignment registry only after safety checks pass.

The scheduled improvement path is handled by `AutoStrategyScheduler` and stays off the
JavaFX thread. Persistent memory is stored by `FileStrategyMemoryRepository` in
`data/auto-strategy-memory.json`.

AI-specific strategy generation lives in `org.investpro.ai.strategy`; shared AI catalog,
credit, provider, and disclaimer classes remain in `org.investpro.ai`.

#### 1. **StrategyParameters** (`org.investpro.strategy.StrategyParameters`)
Defines technical indicator periods and risk thresholds with builder pattern.

```java
StrategyParameters params = StrategyParameters.builder()
    .rsiPeriod(14)
    .emaFast(20)
    .emaSlow(50)
    .minConfidence(0.55)
    .signalAmount(1.0)
    .build();
```

**Merge Support**: Allows variant profiles to override defaults:
```java
StrategyParameters merged = baseParams.merge(styleProfile).merge(riskProfile);
```

#### 2. **StrategyDefinition** (`org.investpro.strategy.StrategyDefinition`)
Immutable definition linking strategy name, base type, and parameters.

```java
StrategyDefinition def = StrategyDefinition.builder()
    .name("Trend Following | Swing | Conservative")
    .baseName("Trend Following")
    .parameters(customParams)
    .build();
```

#### 3. **StrategyCatalog** (`org.investpro.strategy.StrategyCatalog`)
Comprehensive static registry with:
- **17 Core Strategies**: Trend Following, Mean Reversion, Breakout, EMA Cross, etc.
- **11 Style Profiles**: Scalp, Intraday, Swing, Position, Session-based
- **5 Risk Profiles**: Conservative, Balanced, Aggressive, Institutional, Quant
- **6 Market Context Profiles**: FX Core, Crypto, Equities, Futures, Commodities, Index
- **50+ Aliases**: Normalized name resolution

```java
// 1000+ total strategy variants generated automatically
List<String> all = StrategyCatalog.availableStrategyNames();

// Alias resolution
String normalized = StrategyCatalog.normalizeStrategyName("TREND");
// → "Trend Following"

// Definition lookup
StrategyDefinition def = StrategyCatalog.definition("Trend Following");
StrategyDefinition variant = StrategyCatalog.definition("Trend Following | Swing | Conservative");

// Base strategy discovery
String base = StrategyCatalog.resolveBaseStrategyName(variantName);
```

#### 4. **FeaturePipeline** (`org.investpro.strategy.FeaturePipeline`)
Computes technical indicators with **lookahead bias prevention**:

```java
FeaturePipelineConfig config = FeaturePipelineConfig.from(parameters);
FeatureRow features = pipeline.computeLatest(candles, config);
```

**Computed Features**:
- **Trend**: EMA Fast/Slow, Trend Strength
- **Momentum**: RSI, MACD Line/Signal, Momentum
- **Volatility**: ATR, ATR%, Bollinger Bands
- **Breakout**: Historical High/Low (excluding latest candle)
- **Volume**: Volume Ratio, Pullback Gap
- **Regime**: High Volatility / Trending / Ranging

**Lookahead Prevention**:
```java
// Breakout high/low exclude the latest candle
double[] breakout = computeBreakoutLevels(candles, lookback);
// Uses indices: [startIndex, endIndex - 1]
```

#### 5. **FeatureRow** (`org.investpro.strategy.FeatureRow`)
Immutable container for computed features with convenience methods:

```java
FeatureRow features = // ... computed from pipeline
if (features.trendUp() && features.emasAlignedBullish()) {
    // Bullish setup
}

if (features.isHighVolatility()) {
    // Volatility-based strategy
}

double change = features.priceChangePercent();
```

#### 6. **UnifiedStrategy** (`org.investpro.strategy.impl.UnifiedStrategy`)
Single configurable strategy engine implementing all 17 core strategies:

```java
// Create with default base strategy
UnifiedStrategy strategy = new UnifiedStrategy();

// Or specify exact variant
UnifiedStrategy swing = new UnifiedStrategy("Trend Following | Swing | Conservative");

// Switch at runtime
strategy.setStrategyName("Mean Reversion | Scalp | Aggressive");

// Generate signals
StrategySignal signal = strategy.generateSignal(context);
```

## Strategy Implementations

### 17 Core Strategies

1. **Trend Following** - EMA alignment (fast > slow > price)
2. **Mean Reversion** - RSI extremes (oversold/overbought)
3. **Breakout** - Price breaks support/resistance with volume
4. **EMA Cross** - Golden cross (fast > slow) / Death cross (fast < slow)
5. **Momentum Continuation** - Strong directional momentum
6. **Pullback Trend** - Pullback to EMA in trending market
7. **Volatility Breakout** - High ATR with directional move
8. **MACD Trend** - MACD line vs signal alignment
9. **Range Fade** - Bollinger band mean reversion in ranges
10. **Donchian Trend** - Price near Donchian levels in trend
11. **Bollinger Squeeze** - Squeeze breakout
12. **ATR Compression Breakout** - Low volatility followed by volume
13. **RSI Failure Swing** - RSI divergence from price
14. **Volume Spike Reversal** - Volume spike with reversal
15. **Adaptive Momentum Pullback** - Selects strategy based on regime
16. **AI Hybrid** - Requires AI service (placeholder)
17. **ML Model** - Requires AI service (placeholder)

## Signal Flow

```
StrategyContext (candles, market state)
    ↓
UnifiedStrategy.generateSignal()
    ↓
FeaturePipeline.computeLatest()
    ↓
FeatureRow (all computed indicators)
    ↓
Strategy Logic (e.g., trendFollowing())
    ↓
signal() helper (validates confidence, computes stops/targets)
    ↓
StrategySignal (actionable signal)
    ↓
RiskManagementSystem (risk checks)
    ↓
TradeExecutionCoordinator (order execution)
```

## Usage Examples

### Basic Strategy Generation

```java
// Setup
StrategyContext context = StrategyContext.builder()
    .symbol(tradePair)
    .timeframe(Timeframe.H1)
    .candles(historicalCandles)
    .currentPrice(100.0)
    .marketBehavior(MarketBehavior.TRENDING)
    .build();

// Unified strategy with automatic catalog lookup
UnifiedStrategy strategy = new UnifiedStrategy("Trend Following");
StrategySignal signal = strategy.generateSignal(context);

if (signal.isActionable()) {
    // Process through risk management
    riskEngine.evaluateSignal(signal);
}
```

### Dynamic Strategy Selection

```java
// Select variant based on market condition
UnifiedStrategy adaptive = new UnifiedStrategy();

if (context.isHighVolatility()) {
    adaptive.setStrategyName("Volatility Breakout | Scalp | Aggressive");
} else if (context.isLowVolume()) {
    adaptive.setStrategyName("EMA Cross | Swing | Balanced");
} else {
    adaptive.setStrategyName("Trend Following | Intraday | Conservative");
}

StrategySignal signal = adaptive.generateSignal(context);
```

### Alias Resolution

```java
// User-friendly inputs
String input = "TREND";
String normalized = StrategyCatalog.normalizeStrategyName(input);
// → "Trend Following"

UnifiedStrategy strategy = new UnifiedStrategy(normalized);
```

### List Available Strategies

```java
List<String> allStrategies = StrategyCatalog.availableStrategyNames();
// → ~1000 strategy variants

// Filter by base strategy
List<String> trendVariants = allStrategies.stream()
    .filter(name -> name.startsWith("Trend Following"))
    .toList();

// Count total
int total = StrategyCatalog.totalStrategies();
```

## Integration Points

### 1. StrategyInitializer
Automatically registers UnifiedStrategy variants:

```java
StrategyInitializer.initializeStrategies();
// Registers:
// - Trend Following (default)
// - Mean Reversion
// - Breakout
// - Adaptive Momentum Pullback
// - Generic UnifiedStrategy (configurable at runtime)
```

### 2. StrategySelectionService
Uses StrategyCatalog to populate dropdown menus and assign strategies.

### 3. RiskManagementSystem
Receives signals from UnifiedStrategy, validates stops/targets, calculates position size.

### 4. TradeExecutionCoordinator
Executes approved orders from risk-validated signals.

## Parameter Profiles

### Style Profiles (Timeframe/Trading Style)
- **Scalp**: RSI=7, EMA=8/21, ATR=7 (fast indicators)
- **Intraday**: RSI=9, EMA=12/26, ATR=10 (medium)
- **Swing**: RSI=14, EMA=20/50, ATR=14 (standard)
- **Position**: RSI=21, EMA=34/89, ATR=21 (slow)
- Plus: Asia/London/NY Session profiles

### Risk Profiles
- **Conservative**: RSI 32-68, Confidence 0.64, Amount 0.50
- **Balanced**: RSI 35-65, Confidence 0.58, Amount 1.00
- **Aggressive**: RSI 38-62, Confidence 0.54, Amount 1.35
- **Institutional**: RSI 34-66, Confidence 0.60, Amount 0.85
- **Quant**: RSI 33-67, Confidence 0.57, Amount 1.15

### Market Context Profiles
- **FX Core**: Tight parameters for currency pairs
- **Crypto Expansion**: Loose parameters for crypto volatility
- **Equities Macro**: Long-term oriented
- **Futures Carry**: Carry trade optimized
- **Commodities Trend**: Commodity market adapted
- **Index Rotation**: Index/sector rotation optimized

## Safeguards

### No Lookahead Bias
- Breakout levels exclude the latest candle
- All indicators computed on completed candles
- Historical lookbacks use `[startIdx, endIdx - 1]`

### No Direct Order Execution
- UnifiedStrategy returns StrategySignal only
- All signals flow through RiskManagementSystem
- Execution happens in TradeExecutionCoordinator

### No State Mutation
- Feature pipeline is read-only
- Strategy parameters are immutable
- Catalog is thread-safe with synchronized lazy initialization

## Testing

Run unit tests:
```bash
mvn test -Dtest=StrategyCatalogTest
mvn test -Dtest=FeaturePipelineTest
```

### Test Coverage
- ✅ Alias resolution
- ✅ Strategy definition lookup
- ✅ Parameter merging
- ✅ Feature computation
- ✅ Regime detection
- ✅ Lookahead bias prevention
- ✅ Edge cases (insufficient candles, invalid prices)

## Performance

- **Catalog Building**: ~100ms (lazy, on first access)
- **Feature Computation**: ~1-2ms per candle series
- **Signal Generation**: ~5-10ms including feature computation
- **Memory**: ~5MB for full catalog (1000+ definitions)

## Future Enhancements

1. **AI/ML Integration**: Connect to AI service for "AI Hybrid" and "ML Model" strategies
2. **Parameter Optimization**: Auto-tune profiles based on backtest results
3. **Strategy Composition**: Combine multiple base strategies with voting
4. **Regime Switching**: Automatic strategy selection based on market regime
5. **Performance Analytics**: Track strategy win/loss rates and adapt
6. **Custom Variants**: Allow users to create and save strategy variants

## Safety Rules Enforced

✅ **No Private Field Access**: Uses only public SystemCore APIs
✅ **No Order Sending**: Signals only, execution in coordinator
✅ **No Lookahead Bias**: Excludes latest candle from historical lookups
✅ **No State Mutation**: All classes immutable or minimal state
✅ **Thread Safe**: StrategyCatalog uses synchronized initialization
✅ **Null Safe**: Comprehensive null checks and defaults

## Strategy Lab Backtesting Engine

### Overview

The Strategy Lab subsystem provides comprehensive backtesting, performance ranking, consensus voting, and automated strategy assignment capabilities. It allows testing of all available strategies across multiple timeframes and symbols, ranking them by performance, and assigning the best strategies for live trading.

### Architecture

#### Components

1. **StrategyLabService** (`org.investpro.strategy.lab.StrategyLabService`)
   - **Role**: Main orchestration service for all Strategy Lab operations
   - **Singleton Pattern**: Thread-safe lazy initialization with `getInstance()`
   - **Responsibilities**:
     - Run backtests asynchronously for multiple strategies
     - Rank results by performance metrics
     - Generate consensus from strategy votes
     - Manage strategy assignments
     - Provide snapshots for UI display
   - **Thread Model**: Dedicated 4-thread ExecutorService pool named "strategy-lab-worker"

2. **StrategyBacktestRunner** (`org.investpro.strategy.lab.StrategyBacktestRunner`)
   - **Role**: Executes single-strategy backtests on historical data
   - **Key Methods**:
     - `run(StrategyBacktestRequest)`: Execute backtest and return report
   - **Outputs**: `StrategyPerformanceReport` with trade history and metrics

3. **StrategyRankingEngine** (`org.investpro.strategy.lab.StrategyRankingEngine`)
   - **Role**: Scores and ranks performance reports
   - **Scoring Formula**: 
     - Win Rate (25%) + Total Return (20%) + Profit Factor (15%) + Risk/Reward (10%) + Confidence (10%) - Max Drawdown (20%) + Reliability Bonus (0-20%)
   - **Tradability Assessment**: Validates score ≥40, win rate ≥40%, profit factor ≥1.2, drawdown <30%

4. **StrategyVotingEngine** (`org.investpro.strategy.lab.StrategyVotingEngine`)
   - **Role**: Generates consensus from top-ranked strategies
   - **Voting Mechanism**: Weighted voting based on performance score × confidence
   - **Consensus Threshold**: 15% margin between top and competing votes
   - **Output**: `StrategyConsensusResult` with selected strategy

5. **StrategyAssignmentService** (`org.investpro.strategy.lab.StrategyAssignmentService`)
   - **Role**: Manages strategy assignments to symbol/timeframe pairs
   - **Key Features**:
     - Auto-assign best strategy from rankings
     - Manual assignment with locking to prevent auto-replacement
     - Assignment history tracking
     - Prevents trading without valid assignment
   - **Repository**: `StrategyAssignmentRepository` (in-memory, thread-safe)

6. **StrategyLabPanel** (`org.investpro.ui.StrategyLabPanel`)
   - **Role**: JavaFX UI panel for Strategy Lab interaction
   - **Features**:
     - Real-time strategy testing
     - Performance ranking table
     - Voting results display
     - Consensus visualization
     - Active assignment card
     - Manual assignment dialog
   - **Threading**: Async operations with `Platform.runLater()` for thread-safe UI updates

### Wiring & Initialization

#### Bootstrap Process

1. **Application Startup** (`InvestPro.java` main entry point):
   ```
   InvestPro.start() → StrategyBootstrapper.initialize()
   ```

2. **Strategy Framework Initialization** (`StrategyBootstrapper.initialize()`):
   ```
   StrategyInitializer.initializeStrategies()
   → StrategyRegistry.getInstance()
   → initializeStrategyLab()
   ```

3. **Strategy Lab Initialization** (`StrategyBootstrapper.initializeStrategyLab()`):
   ```
   StrategyLabService.getInstance()
   → Lazy-initialize singleton with all components
   → Ready for backtesting operations
   ```

#### Singleton Pattern

All core Strategy Lab components use thread-safe singleton initialization:

**StrategyLabService**:
```java
public static StrategyLabService getInstance() {
    if (instance == null) {
        synchronized (StrategyLabService.class) {
            if (instance == null) {
                instance = new StrategyLabService();
            }
        }
    }
    return instance;
}
```

**StrategyAssignmentRepository**:
```java
public static StrategyAssignmentRepository getInstance() {
    StrategyAssignmentRepository local = instance;
    if (local == null) {
        synchronized (StrategyAssignmentRepository.class) {
            local = instance;
            if (local == null) {
                local = new StrategyAssignmentRepository();
                instance = local;
            }
        }
    }
    return local;
}
```

#### Dependency Injection

Components are initialized with proper dependency resolution:

- **StrategyLabService** creates:
  - StrategyBacktestRunner (no external dependencies)
  - StrategyRankingEngine (no external dependencies)
  - StrategyVotingEngine (uses StrategyRegistry.getInstance())
  - StrategyAssignmentService (uses StrategyAssignmentRepository.getInstance())
  - ExecutorService (4-thread pool)

- **StrategyBacktestRunner** resolves strategies at runtime:
  - Looks up strategies via `StrategyRegistry.getInstance().getStrategy(name)`
  - Falls back to failure report if strategy not found

- **StrategyAssignmentService** uses:
  - StrategyRegistry for strategy validation
  - StrategyAssignmentRepository for persistent storage

### Usage in StrategyEngine

The main trading engine (`StrategyEngine`) integrates with Strategy Lab for automated strategy assignment:

```java
// In StrategyEngine signal generation flow:
1. Check for active assignment: assignmentService.getActiveAssignment(symbol, timeframe)
2. If no assignment: return HOLD with reason "No strategy assignment"
3. If locked/disabled: return HOLD with reason
4. Resolve assigned strategy: assignmentService.resolveAssignedStrategy(symbol, timeframe)
5. Generate signal from assigned strategy
6. Return signal with assignment metadata
```

### UI Integration

**StrategyLabPanel** provides user interface for backtesting:

```java
// User initiates test
labService.testAllStrategies(symbol, timeframes)
    .thenAccept(reports -> {
        // Rank results
        rankings = rankingEngine.rank(reports);
        // Generate consensus
        consensus = votingEngine.vote(symbol, timeframe, context, rankings);
        // Assign best
        assignment = labService.assignBest(symbol, timeframe);
        // Update UI with Platform.runLater()
        Platform.runLater(() -> updateDisplay(rankings, consensus, assignment));
    })
```

### Safety & Threading Model

- **Backtesting Safety**: No live orders sent; pure simulation with position sizing validation
- **Async Execution**: Non-blocking UI with CompletableFuture and ExecutorService
- **Thread Safety**: ConcurrentHashMap caches, synchronized singleton initialization
- **Null Safety**: Comprehensive null checks before Map.copyOf() and event publishing

### Performance Characteristics

- **Backtest Execution**: ~500ms-2s per strategy depending on data size
- **Ranking Computation**: ~10-50ms for 20-50 strategies
- **Consensus Generation**: ~50-100ms per vote calculation
- **Memory**: ~10-20MB for rankings/consensus caches
- **UI Updates**: <100ms for display refresh via Platform.runLater()

### Configuration

ThreadPool size (4 threads) can be adjusted in StrategyLabService constructor:
```java
this.executorService = Executors.newFixedThreadPool(
    4,  // Number of concurrent backtests
    r -> {
        Thread t = new Thread(r, "strategy-lab-worker");
        t.setDaemon(true);
        return t;
    });
```

