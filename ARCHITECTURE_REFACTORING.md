# InvestPro Architecture Refactoring Guide

## Overview

This document defines the refactored architecture for InvestPro that enforces clear separation of concerns across five layers:

1. **SmartBot** - Runtime controller only
2. **SystemCore** - Application wiring and lifecycle owner
3. **TradingWindow** - Desktop UI only
4. **Agents** - Analysis and decision proposals
5. **Risk/Execution** - Trade permission, sizing, and order placement

---

## Layer Responsibilities

### 1. SmartBot (Runtime Controller)

**MUST OWN:**
- `AgentRuntime` - bot execution engine
- `AgentEventBus` - event publishing/subscription
- `AgentRegistry` - registered agents
- `AgentContext` - context for running agents

**MUST CONTROL:**
- `start()` / `stop()` / `restart()` lifecycle
- `setAutoTradingEnabled()` / `setAiReasoningEnabled()` flags
- `setSelectedTradePair()` - which symbol agents trade
- `updateExchange()` - runtime exchange switching

**MUST PUBLISH:**
- `SMART_BOT_STARTED` / `SMART_BOT_STOPPED`
- `AUTO_TRADING_ENABLED` / `AUTO_TRADING_DISABLED`
- `AI_REASONING_ENABLED` / `AI_REASONING_DISABLED`
- `SMART_BOT_SYMBOL_CHANGED` / `SMART_BOT_EXCHANGE_CHANGED`

**MUST NOT:**
- Create exchanges
- Create TradingService
- Create UI windows
- Place trades directly
- Send notifications
- Fetch market data
- Manage database
- Manage user sessions
- Make final trade approval decisions
- Execute BUY/SELL orders

**STATUS:** ✅ Mostly correct. Fixed `eventBusIsUsable()` → `eventBusIsReady()` logic bug.

---

### 2. SystemCore (Application Composition Root)

**RESPONSIBILITIES:**
- Create and own Exchange instances
- Create and own TradingService
- Create and own RiskManagementSystem
- Create and own ExecutionEngine
- Create SmartBot
- Wire all dependencies
- Own application lifecycle
- Start/stop all services cleanly
- Switch exchanges safely
- Load configuration
- Setup account/session/environment

**ENTRY POINTS:**
```java
public void start(TradingService tradingService, TradePair selectedTradePair)
public void stop()
public void connect()
public void disconnect()
public boolean isReady()
```

**DEPENDENCIES CREATED:**
- Exchange (from TradingDesk selection)
- TradingService  
- SmartBot (via BotRuntimeController)
- RiskManagementSystem
- ExecutionEngine
- AiReasoningService
- StrategyEngine
- TradeExecutionCoordinator
- SystemMonitorService
- Notifiers (Telegram, Email)

**MUST NOT:**
- Contain strategy logic
- Contain UI rendering
- Calculate signals
- Execute agent analysis
- Directly place trades

**STATUS:** ⚠️ Needs refactoring to use controller layer. Currently handles too much directly.

---

### 3. TradingWindow (Desktop UI)

**RESPONSIBILITIES:**
- Display charts
- Display selected symbol
- Display account status
- Display bot status
- Display positions/orders/strategy decisions
- Display logs/events
- Provide user buttons (Start Bot, Stop Bot, Enable Auto Trading, etc.)

**INTERACTIONS:**
```java
// Delegate to SystemCore or controllers
systemCore.start(tradingService, pair);
botRuntimeController.setAutoTradingEnabled(true);
manualTradeController.executeBuy("BTC/USDT", 1.0);
```

**MUST NOT:**
- Create exchanges
- Place trades without going through controllers
- Run agents directly
- Perform strategy analysis
- Mutate SmartBot internals
- Own AgentRuntime

**STATUS:** ⚠️ Currently may be calling internal methods. Should call controller layer.

---

### 4. Agents (Analysis and Proposals)

**AGENTS GENERATE:**
- Market analysis events
- Strategy evaluations  
- BotTradeDecision proposals
- Trading signals with confidence scores

**AGENTS PUBLISH:**
- Analysis events to AgentEventBus
- Confidence scores
- Market regime classifications
- Trade recommendations

**AGENTS MUST NOT:**
- Place broker orders directly
- Bypass RiskManagementSystem
- Bypass ExecutionEngine
- Mutate UI
- Control application lifecycle
- Assume auto-trading is enabled
- Execute trades just because a signal exists

**FLOW:**
```
Agent Analysis
  → BotTradeDecision proposal
  → RiskEngine.evaluateTrade(TradeRiskContext)
  → RiskDecision (approved/rejected)
  → ExecutionEngine (if approved & autoTradingEnabled)
  → Exchange places order
  → Events published back to UI
```

**STATUS:** ✅ Generally correct architecture. SignalToDecisionFilter properly intercepts.

---

### 5. Risk / Execution (Trade Permission & Order Placement)

#### RiskManagementSystem (Primary Gatekeeper)

**API:**
```java
public RiskDecision evaluateTrade(TradeRiskContext context)
```

**ACCEPTS:**
- TradeRiskContext (complete trade snapshot)

**RETURNS:**
- RiskDecision with:
  - `approved` - true/false
  - `blockers` - critical failures
  - `warnings` - non-blocking concerns  
  - `finalPositionSize` - risk-adjusted size
  - `finalLeverage` - approved leverage
  - `recommendedExecutionStrategy`
  - `humanReadableSummary`

**GATES CHECKED:**
1. Trading session open?
2. Probability level sufficient?
3. Account has equity/margin?
4. Risk per trade within limit?
5. Daily loss within limit?
6. Exposure limits respected?
7. Expected value positive?
8. Leverage within profile limits?
9. Portfolio heat within limits?

**RULES:**
- ANY blocker → REJECT trade
- Warnings are non-blocking
- Small account mode reduces OANDA position size to 1 unit
- Risk multiplier adjusts based on market conditions
- Psychology profile influences sizing

#### ExecutionEngine & TradeExecutionCoordinator

**RESPONSIBILITIES:**
- Place orders only after risk approval
- Only execute if `autoTradingEnabled == true` (unless user confirms)
- Track order status
- Handle retries safely  
- Prevent duplicate orders
- Prevent conflicting orders
- Publish execution events
- Return OpenOrder on success

**MUST CHECK:**
- Risk approval (RiskDecision.approved == true)
- Auto-trading flag
- User manual confirmation (if manual trade)

**STATUS:** ✅ Already implemented correctly in TradeExecutionCoordinator.

---

## Recommended New Controller Layer

### BotRuntimeController

Simple wrapper around SmartBot for cleaner SystemCore API:

```java
public class BotRuntimeController {
    private final SmartBot smartBot;
    
    public void start(Exchange exchange, TradingService svc, TradePair pair)
    public void stop()
    public void setAutoTradingEnabled(boolean enabled)
    public void setAiReasoningEnabled(boolean enabled)
    public void selectTradePair(TradePair pair)
    
    public boolean isStarted()
    public boolean isAutoTradingEnabled()
    public SmartBot getSmartBot()
}
```

### TradeDecisionPipeline

Orchestrates the signal → risk → execution flow:

```java
public class TradeDecisionPipeline {
    private final RiskManagementSystem riskEngine;
    private final TradeExecutionService executionService;
    
    public TradeDecisionResult evaluate(
        TradeRiskContext context)
    
    public CompletableFuture<OpenOrder> execute(
        TradeDecisionResult result,
        boolean autoTradingEnabled)
}
```

### ManualTradeController

Handles UI user clicks (BUY/SELL/CLOSE):

```java
public class ManualTradeController {
    public CompletableFuture<OpenOrder> executeBuy(String symbol, double qty)
    public CompletableFuture<OpenOrder> executeSell(String symbol, double qty)
    public CompletableFuture<OpenOrder> executeClose(String symbol)
}
```

### TradeRiskContextBuilder

Helper to build TradeRiskContext from signals:

```java
public class TradeRiskContextBuilder {
    public static TradeRiskContext from(
        StrategySignal signal,
        Exchange exchange,
        Account account)
    
    public static TradeRiskContext fromManualTrade(
        String symbol,
        String action,
        double quantity,
        Exchange exchange,
        Account account)
}
```

---

## Message Flow: Automated Trade

```
1. AgentAnalysisAgent generates StrategySignal
2. SignalToDecisionFilter creates BotTradeDecision
3. RiskAgent builds TradeRiskContext from signal
4. RiskManagementSystem.evaluateTrade(context)
   → Returns RiskDecision (approved/rejected/sized)
5. If approved && autoTradingEnabled:
   → ExecutionEngine places order
   → Exchange confirms OpenOrder
   → Events published to AgentEventBus
6. TradingWindow UI updates via event subscription
```

---

## Message Flow: Manual Trade

```
1. User clicks "BUY 1.0 BTC/USDT" in TradingWindow
2. TradingWindow.onBuyButton(symbol, qty)
   → manualTradeController.executeBuy(symbol, qty)
3. ManualTradeController:
   → Builds TradeRiskContext from symbol + qty
   → Calls RiskManagementSystem.evaluateTrade(context)
4. If approved:
   → ExecutionEngine places order
   → Returns OpenOrder
   → Results displayed in TradingWindow
5. If rejected:
   → Shows rejection reason to user
```

---

## Architecture Enforcement Rules

### SmartBot MUST Enforce:
✅ `eventBusIsReady()` (fixed from inverted logic)
✅ Null checks on exchange, tradingService
✅ IllegalStateException if not started
✅ Safe agent cleanup on startup failure

### SystemCore MUST Enforce:
- ✅ Create all services before starting SmartBot
- ⚠️ Delegate bot control to BotRuntimeController
- ⚠️ Delegate manual trades to ManualTradeController
- ⚠️ Use TradeDecisionPipeline for signal processing

### TradingWindow MUST Enforce:
- ⚠️ Never call smartBot methods directly
- ⚠️ Call BotRuntimeController instead
- ⚠️ Call ManualTradeController for user trades
- ✅ Subscribe to AgentEventBus for UI updates

### Agents MUST Enforce:
- ✅ Publish to event bus only
- ✅ Return signals, not execute orders
- ✅ Respect auto-trading flag
- ✅ Never create exchanges

### RiskManagementSystem MUST Enforce:
- ✅ Evaluate ALL trades (not just agents)
- ✅ Return RiskDecision for every call
- ✅ Check session, equity, limits, leverage
- ✅ Provide human-readable summaries

### ExecutionEngine MUST Enforce:
- ✅ Only execute with risk approval
- ✅ Check auto-trading flag
- ✅ Prevent duplicate orders
- ✅ Return OpenOrder on success

---

## Compilation Status

### Fixed ✅
1. SmartBot.eventBusIsUsable() → eventBusIsReady() [LOGIC BUG FIXED]
2. SmartBot unused import removed

### Needs Implementation ⚠️
1. BotRuntimeController (wrapper around SmartBot)
2. TradeDecisionPipeline (orchestrate risk→execution)
3. ManualTradeController (UI manual trades)
4. TradeRiskContextBuilder (helper to build context)

### Needs Refactoring ⚠️
1. SystemCore.start() - delegate to BotRuntimeController
2. SystemCore.stop() - clean shutdown order
3. TradingWindow - call controller layer not SmartBot
4. TradeExecutionCoordinator - expose to pipeline layer

---

## Summary

The refactored architecture enforces clear boundaries:

| Layer | Controls | Does NOT Control |
|-------|----------|------------------|
| SmartBot | Runtime, Events, Flags | Exchanges, Trades, UI |
| SystemCore | Wiring, Lifecycle | Strategies, UI, Orders |
| TradingWindow | UI Display, User Input | Exchanges, Orders, Bot |
| Agents | Analysis, Signals | Trade Execution, UI |
| Risk/Execution | Trade Gates, Orders | Strategies, UI, Lifecycle |

Each layer has one clear responsibility. No circular dependencies. Signal flows in one direction: Agent → Risk → Execution.

