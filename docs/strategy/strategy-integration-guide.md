# Multi-Strategy Framework Integration Guide

## Quick Start: Enable Strategy Selection in Your Trading Bot

### 1. Initialize Strategies at Startup

Call this ONCE during application startup:

```java
StrategyBootstrapper.initialize();
```

This registers all available strategies (TrendFollowing, MeanReversion, Breakout) into the StrategyRegistry singleton.

**Recommended location:** InvestPro.start() method or SystemCore initialization

### 2. Select Strategy for Symbol/Timeframe

When trading a symbol, first retrieve the assigned strategy:

```java
StrategySelectionService selectionService = StrategySelectionService.getInstance();
StrategyAssignment assignment = selectionService.getCurrentAssignment(symbol, timeframe);

if (assignment != null && assignment.isValid()) {
    // Use assigned strategy for this symbol/timeframe
    String strategyId = assignment.getStrategyId();
    TradingStrategy strategy = StrategyRegistry.getInstance().getStrategy(strategyId);
}
```

### 3. Generate Signal from Assigned Strategy

Build a StrategyContext with market data:

```java
StrategyContext context = StrategyContext.builder()
    .symbol(tradePair)
    .timeframe(timeframe)
    .candles(historicalCandles) // List<CandleData>, oldest to newest
    .currentPrice(currentPrice)
    .bid(bidPrice)
    .ask(askPrice)
    .volatility(calculatedVolatility)
    .averageVolume(avgVolume)
    .marketBehavior(detectedBehavior)
    .timestamp(Instant.now())
    .build();

StrategySignal signal = strategy.generateSignal(context);
```

The signal contains:
- `strategy signal`: BUY, SELL, or HOLD
- `confidence`: 0.0-1.0
- `entryPrice`, `stopLoss`, `takeProfit`
- `riskRewardRatio`
- `reasons`: List<String> explaining the signal
- `warnings`: List<String> flagging potential issues

### 4. Manual Strategy Override (Optional)

Force a specific strategy for a symbol/timeframe:

```java
selectionService.manuallyAssign(symbol, timeframe, "trend-following", 
    locked=true, "Manual override reason");
```

- `locked=true` prevents the system from auto-replacing this assignment
- `locked=false` allows auto-replacement when better strategies emerge

### 5. Disable Strategy (Optional)

Temporarily disable a strategy for a symbol:

```java
selectionService.disableStrategy(symbol, timeframe, 
    "Strategy underperforming");
```

## Available Strategies

### 1. TREND_FOLLOWING ("trend-following")
- **Approach:** SMA20/SMA50 crossover
- **Timeframes:** H1, H4, D1, W1 (long-term trends)
- **Assets:** CRYPTO, FOREX, EQUITY on SPOT, PERPETUAL
- **Confidence:** 0.65
- **Best For:** Sustained uptrends/downtrends

### 2. MEAN_REVERSION ("mean-reversion")
- **Approach:** Bollinger Bands (20, 2std) + RSI(14)
- **Timeframes:** M15, M30, H1, H4 (short to medium-term reversions)
- **Assets:** CRYPTO, FOREX, EQUITY on SPOT
- **Confidence:** 0.60
- **Best For:** Range-bound markets with extremes

### 3. BREAKOUT ("breakout")
- **Approach:** Donchian Channel (20-period)
- **Timeframes:** M5, M15, H1, H4, D1
- **Assets:** CRYPTO, FOREX, COMMODITY on SPOT, PERPETUAL
- **Confidence:** 0.68
- **Best For:** Volatility breakouts

## Backtesting & Strategy Ranking

After backtesting strategies, rank them using StrategyRankingEngine:

```java
StrategyRankingEngine rankingEngine = new StrategyRankingEngine();
StrategyScore score = rankingEngine.scoreStrategy(backtestResult);
```

The score includes:
- `totalScore` (0-100): Final weighted score
- `profitabilityScore` (35% weight): Return, Profit Factor, Expectancy
- `riskScore` (25% weight): Drawdown, Sharpe Ratio, Max Loss
- `consistencyScore` (20% weight): Win Rate, Consecutive Losses
- `executionScore` (10% weight): Trade Count, Fees, Slippage
- `stabilityScore` (10% weight): Calmar Ratio, Positive Returns
- `overfittingPenalty` (-0 to -50): Suspicious backtest results

Quality thresholds:
- `isHighQuality()`: score >= 70 AND stability >= 65 AND overfit > -20
- `isAcceptable()`: score >= 55 AND risk >= 50
- `hasRedFlags()`: score < 50 OR stability < 40 OR risk < 30

Store backtest results for later reference:

```java
selectionService.storeBacktestResult(backtestResult);
List<StrategyBacktestResult> results = 
    selectionService.getBacktestResults(symbol, timeframe);
```

## Assignment History & Audit Trail

Track all strategy changes for audit:

```java
StrategySelectionService.StrategyAssignmentHistory history =
    selectionService.getAssignmentHistory(symbol, timeframe);

for (HistoryEntry entry : history.getEntries()) {
    System.out.println(entry.strategyId() + " at " + 
        entry.timestamp() + ": " + entry.note());
}
```

## Architecture

```
TradingStrategy (Interface)
  ├── BaseStrategy (Abstract)
  │   ├── TrendFollowingStrategy
  │   ├── MeanReversionStrategy
  │   └── BreakoutStrategy
  └── [Custom strategies can be added]

StrategyRegistry (Singleton)
  └── Stores and queries all registered strategies

StrategyBootstrapper
  └── Ensures strategies initialized once at startup

StrategySelectionService (Singleton)
  ├── selectAndAssign(): Auto-select best strategy
  ├── manuallyAssign(): User override with optional lock
  ├── disableStrategy(): Disable strategy for symbol/timeframe
  ├── getCurrentAssignment(): Query active assignment
  └── getAssignmentHistory(): Audit trail

StrategyDecisionService
  ├── generateDecision(): Bridge strategy to trading engine
  └── Manages validation, context building, signal generation

StrategyDecisionResult
  └── Wraps decision outcome (success, rejection, signal)

StrategyAssignment
  └── Represents which strategy assigned to symbol/timeframe

StrategyAssignmentRepository (Singleton)
  └── Persists and queries assignments

StrategyBacktestResult
  └── Metrics from backtesting

StrategyRankingEngine
  └── Scores strategies using multi-factor risk-adjusted model

StrategyScore
  └── Result of ranking (0-100 total score + components)
```

## Signal Flow

```
Market Data
    ↓
StrategyDecisionService.generateDecision()
    ↓
StrategySelectionService.getCurrentAssignment()
    ↓
StrategyRegistry.getStrategy()
    ↓
strategy.generateSignal(context)
    ↓
StrategySignal (normalized)
    ↓
RiskManagementSystem.evaluateSignal()
    ↓
RiskDecision (approval/rejection)
    ↓
ExecutionEngine (if approved)
```

## Safety Guarantees

1. **Locked Assignments Cannot Be Auto-Replaced**
   - Manual assignments with locked=true prevent auto-selection
   - System respects user override decisions

2. **All Strategies Return Normalized Signals**
   - Fair comparison across different strategy types
   - Standard StrategySignal format with confidence, strategy signal direction, entry, SL, TP

3. **No Direct Strategy Execution**
   - Strategy signals are advisory only
   - All signals must pass RiskManagementSystem approval
   - Risk engine can adjust sizing, reject, or modify execution

4. **Single Initialization**
   - StrategyBootstrapper prevents duplicate registration
   - Strategies initialized only once at startup

5. **No UI-Driven Trade Execution**
   - Strategy selection happens in core engine, not UI
   - UI can request data and manually override, but not execute
   - Core engine handles strategy→risk→execution pipeline
