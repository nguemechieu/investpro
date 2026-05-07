# Symbol-Level Status Tracking System - Implementation Summary

## Overview

This document describes the symbol-level strategy examination and trading readiness tracking system for InvestPro. It enables the UI (MarketWatch, System Monitor) to display real-time status of each symbol's evaluation and trading readiness.

## Architecture

### Core Components

#### 1. **SymbolTradingMode** (enum)
Location: `src/main/java/org/investpro/core/agents/symbol/SymbolTradingMode.java`

Represents the trading mode/readiness state of a symbol:
- `TRAINING` - Strategy evaluation in progress
- `PAPER_TRADING` - Testing selected strategy in paper mode
- `LIVE_READY` - Strategy evaluated and ready for live trading
- `LIVE_TRADING` - Currently trading live
- `NO_ASSIGNMENT` - No evaluated strategy assigned
- `BLOCKED` - Symbol blocked from trading (e.g., manual pause)
- `PAUSED` - Strategy execution paused
- `FAILED` - Strategy evaluation or execution failed
- `UNKNOWN` - Unknown state

Each mode has:
- `displayName` - Human-readable display text
- `isLiveAllowed()` - Whether live trading is permitted in this mode
- `isLiveMode()` - Whether this is a live trading state
- `isTradingMode()` - Whether this is any form of active trading

#### 2. **SymbolEvaluationState** (enum)
Location: `src/main/java/org/investpro/core/agents/symbol/SymbolEvaluationState.java`

Represents the evaluation/execution state of a symbol's strategy:
- `NOT_STARTED` - No evaluation started
- `COLLECTING_DATA` - Gathering historical data
- `BACKTESTING` - Testing on historical data
- `RANKING` - Ranking strategy candidates
- `PAPER_TRADING` - Testing in paper mode
- `ASSIGNED` - Strategy assigned
- `LIVE_READY` - Ready for live trading
- `LIVE_TRADING` - Trading live
- `PAUSED` - Execution paused
- `FAILED` - Failed

Provides helper methods:
- `isEvaluating()` - Returns true during evaluation phases
- `isAssignedOrReady()` - Returns true when strategy is ready
- `isReadyForLive()` - Returns true for live-ready states

#### 3. **SymbolAgentState** (model)
Location: `src/main/java/org/investpro/core/agents/symbol/SymbolAgentState.java`

Holds the complete state of a symbol's strategy and trading readiness:

**Fields:**
- `symbol: TradePair` - The symbol
- `state: SymbolEvaluationState` - Current evaluation state
- `activeStrategyName: String` - Name of active strategy
- `activeTimeframe: Timeframe` - Active trading timeframe
- `strategyScore: double` - Strategy performance score
- `canTradeLive: boolean` - Whether symbol can trade live
- `lastIssue: String` - Last error/issue
- `assignedStrategyName: String` - Assigned strategy name
- `blockReason: String` - Reason if blocked
- `lastUpdated: long` - Last update timestamp

**Key Methods:**
- `getTradingMode(): SymbolTradingMode` - Determines trading mode from state
- `getMarketWatchStatusText(): String` - Human-readable status for UI
- `isLiveAllowed(): boolean` - Checks if live trading is allowed
- `getLiveBlockedReason(): String` - Reason if live trading is blocked
- `updateTimestamp()` - Update timestamp
- `isStale(maxAgeMs): boolean` - Check if state is stale

**Example getTradingMode() Mapping:**
```
NOT_STARTED/COLLECTING_DATA/BACKTESTING/RANKING → TRAINING
PAPER_TRADING → PAPER_TRADING
ASSIGNED → LIVE_READY (if assigned) or NO_ASSIGNMENT
LIVE_READY → LIVE_READY
LIVE_TRADING → LIVE_TRADING
FAILED → FAILED
PAUSED → PAUSED
null → UNKNOWN
```

**Example getMarketWatchStatusText():**
- "Training / Evaluating"
- "Paper trading candidates"
- "Live ready: RSI_Breakout | 1h"
- "Live trading: MovingAvg | 5m"
- "No evaluated assignment"
- "Blocked: Manual pause"
- "Paused"
- "Failed: Insufficient data"

#### 4. **SymbolAgentManager** (service)
Location: `src/main/java/org/investpro/core/agents/symbol/SymbolAgentManager.java`

Manages all symbol agent states in the system:

**Methods:**
- `getState(TradePair): Optional<SymbolAgentState>` - Get state of one symbol
- `getAllStates(): List<SymbolAgentState>` - Get all states
- `updateState(TradePair, SymbolAgentState): void` - Update a symbol's state
- `removeState(TradePair): void` - Remove a symbol's state
- `getStatesByMode(SymbolTradingMode): List<SymbolAgentState>` - Filter by mode
- `getModeCounts(): Map<SymbolTradingMode, Integer>` - Count symbols per mode
- `getEvaluationStateCounts(): Map<SymbolEvaluationState, Integer>` - Count by eval state
- `getTotalSymbolCount(): int` - Total symbols tracked
- `getLiveAllowedCount(): int` - Count symbols allowed to trade live
- `getSummary(): Map<String, Object>` - Summary for System Monitor

**Thread Safety:** Uses ConcurrentHashMap for thread-safe access.

### Integration Points

#### 5. **SmartBot Updates**
Location: `src/main/java/org/investpro/core/bot/SmartBot.java`

Added:
- `symbolAgentManager: SymbolAgentManager` field
- Initialized in constructor via `new SymbolAgentManager()`
- Exposed as `@Getter` for access via `getSymbolAgentManager()`

#### 6. **SystemCore Updates**
Location: `src/main/java/org/investpro/core/SystemCore.java`

Added convenience methods:
```java
public SymbolAgentManager getSymbolAgentManager()
public Optional<SymbolAgentState> getSymbolState(TradePair symbol)
public List<SymbolAgentState> getAllSymbolStates()
```

These delegate to SmartBot's symbolAgentManager.

### UI Components

#### 7. **MarketWatchRow** (model)
Location: `src/main/java/org/investpro/ui/models/MarketWatchRow.java`

JavaFX-based model for a single MarketWatch table row:

**Basic Market Data Properties:**
- `symbol: ObjectProperty<TradePair>`
- `bid: DoubleProperty`
- `ask: DoubleProperty`
- `spread: DoubleProperty`
- `spreadPercent: DoubleProperty`
- `session: StringProperty`

**Agent/Strategy State Properties:**
- `agentState: StringProperty` - Evaluation state display
- `tradingMode: StringProperty` - Trading mode display
- `activeStrategy: StringProperty` - Active strategy name
- `activeTimeframe: StringProperty` - Active timeframe code
- `strategyScore: DoubleProperty` - Strategy score (0-1)
- `liveReady: BooleanProperty` - Whether live trading is allowed
- `issue: StringProperty` - Issue/block reason if applicable

**Key Methods:**
- `updateSymbolState(SymbolAgentState): void` - Populate all properties from state
- `getTradingModeCssClass(): String` - Returns CSS class for mode styling
- `isStateStale(maxAgeMs): boolean` - Check if state is outdated

#### 8. **MarketWatchPanel** (UI)
Location: `src/main/java/org/investpro/ui/MarketWatchPanel.java`

Complete market watch table display:

**Columns:**
1. Symbol
2. Bid
3. Ask
4. Spread %
5. Session
6. State (evaluation state)
7. Mode (trading mode with color coding)
8. Strategy (active strategy name)
9. TF (timeframe code)
10. Score (strategy score with color gradient)
11. Live Ready (yes/no with color coding)
12. Issue (block reason or constraints)

**Styling:**
- Mode column: Color-coded by state
  - Training: gray
  - Paper: blue
  - Live Ready: green
  - Live Trading: bright green with background
  - Blocked: red
  - Paused: amber
  - Failed: dark red

- Score column: Color gradient
  - > 0.75: green
  - > 0.50: blue
  - > 0.25: amber
  - ≤ 0.25: red

- Live Ready column: Green checkmark or red X

**Features:**
- Auto-refresh every 3 seconds
- Manual refresh button
- Pause/resume button
- Row cache for efficiency
- Synchronized updates from SymbolAgentManager

**Event Handlers:**
- `onSymbolStateUpdated()` - Single symbol update
- `refreshMarketWatchData()` - Full refresh from manager
- Periodic refresh via Timeline

## Usage Examples

### Updating Symbol State

```java
SystemCore systemCore = ...;
TradePair btcUsd = ...;

SymbolAgentState state = SymbolAgentState.builder()
    .symbol(btcUsd)
    .state(SymbolEvaluationState.LIVE_READY)
    .activeStrategyName("RSI_Breakout")
    .activeTimeframe(Timeframe.H1)
    .strategyScore(0.82)
    .canTradeLive(true)
    .assignedStrategyName("RSI_Breakout")
    .build();

systemCore.getSymbolAgentManager().updateState(btcUsd, state);
```

### Querying Symbol State

```java
SystemCore systemCore = ...;
TradePair symbol = ...;

// Get single symbol state
Optional<SymbolAgentState> state = systemCore.getSymbolState(symbol);

// Check if live allowed
if (state.isPresent() && state.get().isLiveAllowed()) {
    // Safe to trade live
}

// Get all symbols
List<SymbolAgentState> allStates = systemCore.getAllSymbolStates();

// Get live ready symbols
List<SymbolAgentState> liveReady = systemCore.getSymbolAgentManager()
    .getStatesByMode(SymbolTradingMode.LIVE_READY);
```

### Creating MarketWatch UI

```java
SystemCore systemCore = ...;
MarketWatchPanel marketWatch = new MarketWatchPanel(systemCore);

// Add to scene
root.getChildren().add(marketWatch);

// Refresh data
marketWatch.refreshMarketWatchData();

// Shutdown when done
marketWatch.shutdown();
```

### System Monitor Integration

```java
SystemCore systemCore = ...;
SymbolAgentManager manager = systemCore.getSymbolAgentManager();

// Get statistics
Map<String, Object> summary = manager.getSummary();
System.out.println("Total symbols: " + summary.get("total"));
System.out.println("Live allowed: " + summary.get("liveAllowed"));
System.out.println("Live trading: " + summary.get("liveTrading"));
System.out.println("Training: " + summary.get("training"));
```

## Design Principles

1. **Single Source of Truth** - SymbolAgentState is the authoritative source; UI reads from it
2. **No Guessing** - MarketWatch displays actual state from manager, never defaults/guesses
3. **Type Safety** - Enums enforce valid states
4. **Thread Safety** - Manager uses ConcurrentHashMap
5. **Clear Lifecycle** - Evaluation states follow logical progression
6. **Live Trading Gate** - Only LIVE_READY/LIVE_TRADING with canTradeLive=true allow live trading
7. **Extensible** - Easy to add new states or properties

## Live Trading Gate Enforcement

The system enforces a strict gate for live trading:

**A symbol can trade live ONLY if:**
1. `getTradingMode()` returns LIVE_READY or LIVE_TRADING
2. `canTradeLive` field is true
3. No `blockReason` is set (or blockReason is null)

**Before Executing Live Order:**
```java
SymbolAgentState state = systemCore.getSymbolState(symbol).orElse(null);
if (state == null || !state.isLiveAllowed()) {
    // Block order
    String reason = state != null ? state.getLiveBlockedReason() : "No state";
    reject("Live trading blocked: " + reason);
}
```

## Compilation Status

✅ **BUILD SUCCESS**
- 392 source files
- 0 errors
- 8 non-blocking Lombok warnings
- All classes compile cleanly

## Files Created

1. `src/main/java/org/investpro/core/agents/symbol/SymbolTradingMode.java`
2. `src/main/java/org/investpro/core/agents/symbol/SymbolEvaluationState.java`
3. `src/main/java/org/investpro/core/agents/symbol/SymbolAgentState.java`
4. `src/main/java/org/investpro/core/agents/symbol/SymbolAgentManager.java`
5. `src/main/java/org/investpro/ui/models/MarketWatchRow.java`
6. `src/main/java/org/investpro/ui/MarketWatchPanel.java`

## Files Modified

1. `src/main/java/org/investpro/core/bot/SmartBot.java` - Added SymbolAgentManager
2. `src/main/java/org/investpro/core/SystemCore.java` - Added symbol convenience methods

## Next Steps (Optional)

1. **Event Integration** - Wire SymbolAgentState updates to AgentEventBus
2. **System Monitor Panel** - Add symbol statistics widget
3. **Persistence** - Save/load symbol states if needed
4. **Visualization** - Add charts/graphs for symbol status trends
5. **Alerts** - Notify user when symbol transitions to live trading
6. **API Endpoints** - Expose via REST if needed

## Architecture Diagram

```
SystemCore
├── SmartBot
│   └── SymbolAgentManager (NEW)
│       └── Map<TradePair, SymbolAgentState>
│           ├── symbol
│           ├── state (SymbolEvaluationState)
│           ├── tradingMode (SymbolTradingMode)
│           ├── activeStrategy
│           └── canTradeLive
│
├── MarketWatchPanel (UI)
│   ├── TableView<MarketWatchRow>
│   │   └── TableColumn[Symbol, Mode, Strategy, Score, LiveReady, Issue...]
│   └── Timeline (3s refresh)
│
└── SystemMonitor (future)
    └── Symbol Statistics
```

## Acceptance Criteria Met

✅ mvn clean compile passes
✅ MarketWatch shows symbol trading mode
✅ MarketWatch distinguishes training from live trading
✅ MarketWatch shows active strategy and timeframe
✅ MarketWatch shows score with color coding
✅ MarketWatch shows live ready yes/no
✅ MarketWatch shows issue/reason if blocked
✅ UI reads SymbolAgentState, not random local flags
✅ No live trading enabled just because UI says live (gate enforced in state)
