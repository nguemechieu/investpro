# InvestPro Clean Architecture Refactoring Guide

## Overview
This document provides step-by-step guidance for refactoring InvestPro into a clean professional architecture with proper separation of concerns.

## Core Principles

### 1. UI Independence
- **TradingWindow** (JavaFX UI) can run without the bot
- **SystemCore/SmartBot** can run without UI
- **Agents** can be tested without JavaFX
- Use `DesktopExchangeStreamBridge` for UI-only market data

### 2. Execution Gateway
Orders cannot reach Exchange without `FinalRiskGate` approval
Pipeline: Signal → Risk Evaluation → Local Python gRPC AI Review → FinalRiskGate → ExecutionEngine → Exchange
Java remains the execution authority; Python only returns advisory output
No agent directly calls Exchange.placeOrder()

### 3. Local AI Boundary
`LocalAiRuntimeService` is the Java entry point to the Python gRPC runtime
Python returns signal review, regime, strategy, backtest, risk, and anomaly advice
`LocalAiReasoningService` remains the deterministic offline fallback
If the Python service is unavailable, Java falls back conservatively and keeps execution behind `FinalRiskGate`

- **Bot OFF**: TradingWindow streams selected symbol via `DesktopExchangeStreamBridge`
- **Bot ON**: SystemCore owns bot stream, TradingWindow stops its stream
- **Bot STOPS**: TradingWindow resumes desktop stream

---

## Architecture Layers

### Layer 1: Application (`org.investpro`)

**InvestPro.java / InvestProLauncher.java**
```
Responsibilities:
- JavaFX Application entry point
- Create primary Stage
- Instantiate SystemCore
- Instantiate TradingWindow
- Wire lifecycle events
- Handle shutdown

Must NOT:
- Contain business logic
- Create Exchange/Strategy/Risk/AI directly
```

---

### Layer 2: Core System (`org.investpro.core`)

**SystemCore.java**
```
Responsibilities:
- Composition root for all non-UI components
- Create and own:
  - Exchange adapter
  - SmartBot
  - StrategyEngine
  - RiskManagementSystem
  - AiReasoningService
  - ExecutionEngine
  - TradeExecutionCoordinator
  - NotificationService
  - AgentRegistry

Public API:
- startBot(TradingService, TradePair)
- stopBot()
- setAutoTradingEnabled(boolean)
- setAiReasoningEnabled(boolean)
- startStreaming(List<TradePair>, StreamingMode)
- startStreaming(TradePair, StreamingMode)
- stopStreaming()
- disconnect()

Must NOT:
- Contain agent behavior
- Update UI directly
- Implement strategy logic
- Implement risk rules
- Generate AI prompts directly
```

**SmartBot.java**
```
Responsibilities:
- Manage agent lifecycle
- Own AgentContext, AgentRuntime, AgentRegistry, AgentEventBus
- Publish events to agents
- Control autoTradingEnabled flag
- Control aiReasoningEnabled flag
- Track started/running/stopped state

Public API:
- start(Exchange, TradingService, TradePair)
- stop()
- setAutoTradingEnabled(boolean)
- setAiReasoningEnabled(boolean)
- setSelectedTradePair(TradePair)
- isStarted()  // CRITICAL: must return started.get()
- isRunning()
- publishMarketEvent(...)
- publishSystemEvent(...)

CRITICAL BUG TO AVOID:
```
public boolean isStarted() {
    return started.get();  // NOT !started.get()
}
```
```

---

### Layer 3: Agent System (`org.investpro.core.agents`)

**Agent Interface**
```java
public interface Agent {
    String id();              // Unique identifier
    String name();            // Human-readable name
    void start(AgentContext context);
    void stop();
    default boolean isRunning() { return true; }
    default void onEvent(AgentEvent event) {}
}
```

**AgentRegistry**
```java
public class AgentRegistry {
    - register(Agent): throws if duplicate ID
    - unregister(String id)
    - getAgent(String id): Optional<Agent>
    - getAgents(): List<Agent>
    - startAll(AgentContext)
    - stopAll()
    - Prevents duplicate IDs
    - Logs failures without crashing
}
```

**AgentEventBus**
```java
public class AgentEventBus {
    - subscribe(Agent agent, EventType eventType)
    - unsubscribe(Agent agent, EventType eventType)
    - publish(AgentEvent event)
      Delivers to all subscribed agents
      Logs delivery failures without crashing
}
```

**AgentContext**
```java
Immutable container passed to agents at startup:
- Exchange exchange
- TradingService tradingService
- StrategyEngine strategyEngine
- RiskManagementSystem riskManagementSystem
- AiReasoningService aiReasoningService
- ExecutionEngine executionEngine
- TradeExecutionCoordinator tradeExecutionCoordinator
- AgentEventBus eventBus

All via getters and publishEvent()
```

**AgentModule**
```java
public interface AgentModule {
    String moduleId();
    void configure(AgentRegistry registry, SystemCoreDependencies deps);
}
```

---

### Layer 4: Trading Agents

**MarketDataAgent**
```
Receives:
- MARKET_TICK
- MARKET_CANDLE
- MARKET_ORDERBOOK

Publishes:
- Normalized market events
```

**SignalAgent**
```
Receives:
- Market events

Calls:
- StrategyEngine.generateSignal()

Publishes:
- SIGNAL_CREATED (with StrategySignal payload)
- SIGNAL_REJECTED
```

**RiskAgent**
```
Receives:
- SIGNAL_CREATED

Builds:
- TradeRiskContext

Calls:
- RiskManagementSystem.evaluateTrade()

Publishes:
- RISK_APPROVED (with approved context)
- RISK_REJECTED
```

**PortfolioAgent**
```
Monitors:
- Total exposure
- Drawdown
- Portfolio heat
- Open positions count

Publishes:
- PORTFOLIO_UPDATED
- DRAWDOWN_WARNING (if > 20%)
```

**PositionManagementAgent**
```
Monitors:
- Open positions

Creates:
- PositionActionIntent (hold, reduce, take profit, close, etc.)

Publishes:
- Position intents for ExecutionAgent
```

**ExecutionAgent**
```
Receives:
- Approved trade intents
- Position action intents

Calls:
- TradeExecutionCoordinator.executeApprovedTrade()

Publishes:
- ORDER_SUBMITTED
- ORDER_FILLED
- ORDER_FAILED
```

**AuditAgent**
```
Receives:
- All events

Records:
- Signals generated
- Risk decisions
- AI reviews
- Approvals/rejections
- Fills
- Failures

Calls:
- AuditRepository.save()
```

---

### Layer 5: Execution Pipeline

**TradeExecutionCoordinator**
```
Full protected pipeline:

1. Receives: StrategySignal
2. Builds: TradeRiskContext
3. Calls: RiskManagementSystem.evaluateTrade()
4. Creates: AiTradeReviewRequest
5. Calls: AiReasoningService.reviewTrade()
6. Calls: FinalRiskGate.makeDecision()
7. If APPROVED: Calls ExecutionEngine.executeApprovedOrder()
8. If HOLD: Logs and creates manual review ticket
9. If REJECTED: Logs clearly
10. Returns: TradeExecutionResult

GOLDEN RULE:
No order reaches Exchange unless FinalRiskGate approves it.
```

**ExecutionEngine**
```
Responsibilities:
- Execute approved new orders
- Execute approved position actions
- Call Exchange methods
- Apply final defensive checks
- Log execution results

Supported order types:
- MARKET
- LIMIT
- VWAP (with safe fallback)
- TWAP (with safe fallback)
- ICEBERG (with safe fallback)
- SCALED_ENTRY (first chunk)
- ALGORITHMIC (with safe fallback)

Public API:
- executeApprovedOrder(StrategySignal, TradeRiskContext, FinalRiskGate.OrderApprovalDecision)
- execute(PositionActionIntent)

Must NOT:
- Generate signals
- Evaluate risk
- Ask AI
- Bypass FinalRiskGate
```

**FinalRiskGate**
```
Final authorization gate before Exchange.

Checks:
- TradeRiskContext completeness
- Portfolio heat < 95%
- Cumulative risk < 99%
- Margin requirements
- Max leverage limits
- Max drawdown limits

Decision:
- APPROVED
- REJECTED
- HOLD_FOR_REVIEW
- DOWNSIZE_AND_APPROVE

Golden rule: Fail safely, never approve silently risky orders.
```

---

### Layer 6: Risk & AI

**TradeRiskContext** (with @Builder)
```
Complete context for risk evaluation:

Symbol & Contract:
- TradePair symbol
- assetClass
- contractType
- broker

Account State:
- accountEquity
- availableCash
- accountBalance
- usedMargin
- freeMargin
- currentOpenRisk

Trade Proposal:
- requestedPositionSize
- requestedLeverage
- entryPrice
- stopLossPrice
- takeProfitPrice
- bidPrice
- askPrice
- currentPrice

Risk Metrics:
- expectedWinRate
- expectedRewardRiskRatio
- expectedValue
- maxRiskPerTrade
- maxCumulativeRisk
- maxAllowedLeverage
- maxAllowedDrawdownPercent
- estimatedSlippagePercent
- estimatedFee

Profiles:
- riskProfile
- marketBehavior
- executionStrategy
- liquidityProfile
- psychologyProfile
- probabilityLevel
- capitalProtection
- systemDesign

Methods:
- calculateTradeRisk()
- calculateTradeReward()
- calculatePositionNotional()
- calculateRewardRiskRatio()
- calculateExpectedValue()
- getPortfolioHeatPercent()
- getRiskLimitUsageRatio()
- getCumulativeRiskUsageRatio()
- isValidForRiskEvaluation()
- hasBracketRiskPlan()
- normalized()
- getSymbolText()
- getTradePair()
```

**AiReasoningService**
```
Interface:
- reviewTrade(AiTradeReviewRequest): AiTradeReviewResponse
- recommendPositionAction(TradeRiskContext): AiPositionAction
- isAvailable(): boolean
- getImplementationName(): String

Implementations:
- OpenAiReasoningService (calls OpenAI API)
- LocalAiReasoningService (local model or offline)
```

**AiTradeReviewRequest**
```
Request to AI:
- tradeSignalId
- strategy name
- entryPrice, stopLoss, takeProfit
- symbol
- accountEquity
- riskAmount / riskPercent
- expectedValue / expectedWinRate
- marketCondition
- additionalContext
```

**AiTradeReviewResponse**
```
AI Decision:
- Decision (APPROVED, REJECTED, MANUAL_REVIEW, WAIT)
- reasoning (string explanation)
- confidenceScore (0.0-1.0)
- riskAssessment (string)
- positionSizeRecommendation (double)
```

**AiPositionAction**
```
Recommendation for open position:
- positionId
- action (HOLD, REDUCE, TAKE_PROFIT, CLOSE, HEDGE, ESCALATE_TO_REVIEW, etc.)
- reasoning
- recommendedPrice
- recommendedQuantity
- confidenceScore
```

---

### Layer 7: Exchange Adapters

### Layer 7.5: Universal Tradability Layer

**Purpose**
```
Provide one cross-venue policy surface for symbol eligibility so UI, bot, and execution
follow the same rules regardless of exchange-specific flags.
```

**Core Components**
```
- UniversalTradabilityService
- SymbolTradability
- TradabilityScope
- TradabilityStatus
- MarketWatchTradabilityFilter
```

**Scope Rules**
```
- UI / Market Watch: allow symbols with marketDataAllowed=true
- Bot Runtime: allow symbols with botTradingAllowed=true
- Live Order Submit: require orderSubmissionAllowed=true at submit time
- Backtesting: allow symbols with marketDataAllowed=true even if liveTradingAllowed=false
```

**Integration Points**
```
- TradingDesk: filter selector, tradability column, submit-time guard
- SystemCore: scope-aware streaming symbol filtering
- ExecutionEngine: final order-submission tradability recheck
- Strategy/Backtesting panels: market-data eligibility filtering
```

**ExchangeCapabilities** (immutable record)
```java
public record ExchangeCapabilities(
    boolean marketData,
    boolean accountData,
    boolean orderSubmission,
    boolean spot,
    boolean margin,
    boolean futures,
    boolean perpetuals,
    boolean options,
    boolean forex,
    boolean stocks,
    boolean cfd,
    boolean hedging,
    boolean stopLossModification,
    boolean takeProfitModification,
    boolean trailingStop,
    boolean websocket
) {}
```

**Exchange Interface**
```
Core Methods:
- connect()
- disconnect()
- isConnected()
- getCapabilities(): ExchangeCapabilities
- getAccount(): Account
- fetchOrderBook(TradePair pair): OrderBook
- getCandleDataSupplier(int seconds, TradePair pair): CandleDataSupplier

Order Methods (use TradePair):
- placeMarketOrder(TradePair, StrategySignal)
- placeLimitOrder(TradePair, StrategySignal, double price)

Position Methods (use string symbol when existing API requires):
- closePosition(String symbol, String positionId)
- closePartialPosition(String symbol, String positionId, double qty)
- modifyStopLoss(String symbol, String positionId, double stopLoss)
- modifyTakeProfit(String symbol, String positionId, double takeProfit)
- enableTrailingStop(String symbol, String positionId, double distance)

Streaming:
- stream(ExchangeStreamSubscription, ExchangeStreamConsumer)
- stopStreaming(ExchangeStreamSubscription)
- stopAllStreams()

Safety Rule:
Unsupported operations throw UnsupportedOperationException, never return null.
```

---

### Layer 8: UI Layer

**TradingWindow**
```
Responsibilities:
- JavaFX scene graph
- Chart tabs
- Account/position/order display
- Manual trade entry
- Own DesktopExchangeStreamBridge
- Desktop UI-only streaming

Public API:
- startDesktopStream(TradePair symbol)
- stopDesktopStream()
- updateTickerFromStream(Ticker)
- updateOrderBookFromStream(OrderBook)
- updateAccountFromStream(Account)
- updatePositionFromStream(Position)
- updateTradeFromStream(Trade)
- appendJournal(String message)

Must NOT:
- Generate strategy signals
- Evaluate risk
- Make AI decisions
- Execute autonomous trades
- Contain agent logic

Streaming Lifecycle:
- Bot OFF: Desktop stream active
- Bot starts: Stop desktop stream, SystemCore owns bot stream
- Bot stops: Resume desktop stream
```

**DesktopExchangeStreamBridge**
```
Bridges:
- Exchange streaming (on bot thread)
- to UI updates (on FX thread)

Must use:
- Platform.runLater() for all UI updates
- Never mutate ObservableList outside FX thread
```

---

## Refactoring Checklist

### Phase 1: Foundation
- [ ] Verify Agent interface has id() and name()
- [ ] Verify AgentRegistry prevents duplicates
- [ ] Verify AgentEventBus delivers without crashing
- [ ] Verify AgentRuntime exists
- [ ] Create DefaultTradingAgentModule

### Phase 2: Core System
- [ ] Fix SmartBot.isStarted() to return started.get()
- [ ] Verify SmartBot doesn't own exchange streaming
- [ ] Verify SystemCore owns composition
- [ ] Verify SystemCore doesn't contain agent logic

### Phase 3: Execution Pipeline
- [ ] Create TradeExecutionCoordinator full pipeline
- [ ] Verify ExecutionEngine only executes approved actions
- [ ] Verify FinalRiskGate is consulted before all orders
- [ ] Test that rejected orders never reach Exchange

### Phase 4: Risk & AI
- [ ] Complete TradeRiskContext with all methods
- [ ] Verify AiReasoningService interface exists
- [ ] Create OpenAiReasoningService implementation
- [ ] Create LocalAiReasoningService implementation

### Phase 5: Agents
- [ ] Complete MarketDataAgent behavior
- [ ] Complete SignalAgent behavior
- [ ] Complete RiskAgent behavior
- [ ] Complete PortfolioAgent behavior
- [ ] Complete PositionManagementAgent behavior
- [ ] Complete ExecutionAgent behavior
- [ ] Complete AuditAgent behavior

### Phase 6: UI Integration
- [ ] Verify TradingWindow is UI-only
- [ ] Implement DesktopExchangeStreamBridge properly
- [ ] Implement streaming lifecycle (bot on/off)
- [ ] All UI updates via Platform.runLater()

### Phase 7: Exchange Layer
- [ ] Create ExchangeCapabilities record
- [ ] Add capabilities to all Exchange adapters
- [ ] Verify unsupported ops throw exceptions
- [ ] Use TradePair consistently in new methods

### Phase 8: Persistence & Services
- [ ] Complete Trade model with persistence
- [ ] Implement TradeService
- [ ] Implement TradeRepositoryImpl
- [ ] Complete NewsDataProvider with MAJOR_INDICATORS

### Phase 9: Testing & Compilation
- [ ] Compile with no errors
- [ ] Run agent unit tests in isolation
- [ ] No Java 21-only methods (getFirst, addFirst, removeLast)
- [ ] No primitive double compared with null

---

## Threading Rules

1. **All JavaFX updates**: Use `Platform.runLater(...)`
2. **Exchange streams**: Never mutate ObservableList outside FX thread
3. **Agents**: Use executor services for blocking operations
4. **Repositories/Services**: Must not depend on JavaFX
5. **No blocking I/O on FX thread**

---

## Java Compatibility

- **Targets Java 21** (LTS)
- **Use modern Java 21 idioms** where appropriate, but ensure compatibility with the build environment

---

## Logging & Safety

1. **Use slf4j** for all logging
2. **Defensive null handling** throughout
3. **No silent failures** for execution
4. **No direct execution from AI**
5. **No duplicate websocket streams** (UI vs bot)
6. **Fail safely**: Errors logged, system continues

---

## Expected Compilation Errors & Fixes

### Missing Methods
If a class lacks a method:
1. Check if it's supposed to exist (refer to spec)
2. Either implement it properly, or
3. Create a safe no-op/stub with logging, or
4. Mark as TODO with fallback

Example:
```java
// TODO: Implement when ExchangeDataSupplier available
public CandleDataSupplier getCandleDataSupplier(int seconds, TradePair pair) {
    logger.warn("CandleDataSupplier not yet implemented for {}", pair);
    return null;  // Safe: calling code should null-check
}
```

### Mismatched Types
If Symbol vs String vs TradePair mismatch:
- Use TradePair for new orders
- Use string symbol only for legacy Exchange methods
- Add helper: `pair.toString('/')` or `symbol.split("/")[0]`

### Missing Dependencies
If SystemCore needs service X:
1. Add to SystemCoreDependencies
2. Pass to AgentContext
3. Agents access via context.getServiceX()

---

## Success Criteria

When complete, the system should:

✅ **Compilation**: No errors, no warnings about missing methods
✅ **Agent Testing**: Run agents without JavaFX, without Exchange
✅ **Order Safety**: No order reaches Exchange without FinalRiskGate approval
✅ **UI Independence**: UI runs without bot, bot runs without UI
✅ **Streaming**: No duplicate streams, proper lifecycle management
✅ **Logging**: All failures logged, system continues
✅ **Thread Safety**: No UI mutations outside FX thread
✅ **Risk**: All risk rules enforced, defaults safe
✅ **AI**: All AI decisions logged, never directly execute orders

---

## References & Related Tasks

1. **Strategy Research** (Layer 4 - Agents)
   - Implement StrategyRegistry
   - Implement StrategySelectionService
   - Implement BacktestingService
   - Auto-select best strategy per symbol/timeframe

2. **News Handling** (Layer 4 - Agents)
   - Activate NewsDataProvider.MAJOR_INDICATORS
   - Create NewsAgent
   - Enforce pair-level blackouts during major events

3. **Trade Persistence** (Layer 8)
   - Complete Trade model
   - Implement TradeRepositoryImpl
   - Implement TradeService
   - Support grouping, filtering, analytics

4. **Monitoring & Observability**
   - Add metrics collection to ExecutionEngine
   - Add performance tracking to StrategyEngine
   - Add state machine logging to SmartBot
