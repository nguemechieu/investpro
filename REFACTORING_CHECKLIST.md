# Critical Refactoring Checklist - Priority Order

## 🔴 CRITICAL (Must Fix Immediately)

### 1. SmartBot.isStarted() Bug
**File**: `src/main/java/org/investpro/core/bot/SmartBot.java`
**Issue**: Must return `started.get()`, not `!started.get()`
**Impact**: Bot state tracking broken, agents may start twice or not at all
```java
// WRONG:
public boolean isStarted() {
    return !started.get();  // ❌ Inverted!
}

// CORRECT:
public boolean isStarted() {
    return started.get();   // ✅
}
```

### 2. FinalRiskGate Implementation
**File**: Create `src/main/java/org/investpro/ai/FinalRiskGate.java`
**Why**: No order should reach Exchange without approval
**Must include**:
- OrderApprovalDecision enum (APPROVED, REJECTED, HOLD, DOWNSIZE)
- makeDecision(TradeRiskContext) method
- Portfolio heat check (< 95%)
- Cumulative risk check (< 99%)

### 3. TradeExecutionCoordinator Full Pipeline
**File**: `src/main/java/org/investpro/core/agents/execution/TradeExecutionCoordinator.java`
**Must implement**:
```java
public TradeExecutionResult executeTrade(StrategySignal signal, TradeRiskContext riskContext) {
    // 1. Validate RiskContext
    // 2. Call RiskManagementSystem.evaluateTrade()
    // 3. Build AiTradeReviewRequest
    // 4. Call AiReasoningService.reviewTrade()
    // 5. Call FinalRiskGate.makeDecision()
    // 6. If approved: ExecutionEngine.executeApprovedOrder()
    // 7. Return TradeExecutionResult
}
```

### 4. ExecutionEngine Only Executes Approved Orders
**File**: `src/main/java/org/investpro/core/agents/execution/ExecutionEngine.java`
**Verify**:
- ✅ No direct strategy signal handling
- ✅ No risk evaluation
- ✅ No AI decision making
- ✅ Only executes FinalRiskGate-approved orders
- ✅ All orders come through ExecutionEngine, not direct Exchange calls

---

## 🟠 HIGH PRIORITY (Phase 1)

### 5. DefaultTradingAgentModule Registration
**File**: `src/main/java/org/investpro/core/agents/modules/DefaultTradingAgentModule.java`
**Must**:
- Register MarketDataAgent
- Register SignalAgent
- Register RiskAgent
- Register PortfolioAgent
- Register PositionManagementAgent
- Register ExecutionAgent
- Register AuditAgent
- Handle registration failures gracefully

### 6. Agent Interface Consistency
**File**: `src/main/java/org/investpro/core/agents/Agent.java`
**Must have**:
```java
String id();                    // Unique identifier
String name();                  // Human-readable name
void start(AgentContext context);
void stop();
default boolean isRunning() { return true; }
default void onEvent(AgentEvent event) {}
```

### 7. AgentRegistry Duplicate Prevention
**File**: `src/main/java/org/investpro/core/agents/AgentRegistry.java`
**Must**:
- Throw `IllegalStateException` if agent ID already registered
- Log all registration/unregistration
- Prevent duplicate IDs

### 8. SystemCore Composition Root
**File**: `src/main/java/org/investpro/core/SystemCore.java`
**Must own**:
- Exchange instance
- SmartBot instance
- StrategyEngine
- RiskManagementSystem
- AiReasoningService
- ExecutionEngine
- TradeExecutionCoordinator
- NotificationService
- AgentRegistry

**Must expose**:
```java
void startBot(TradingService tradingService, TradePair pair);
void stopBot();
void setAutoTradingEnabled(boolean enabled);
void setAiReasoningEnabled(boolean enabled);
SmartBot getSmartBot();
```

**Must NOT**:
- Contain agent behavior
- Update UI directly
- Create Exchange streams

---

## 🟡 MEDIUM PRIORITY (Phase 2)

### 9. TradeRiskContext Completeness
**File**: `src/main/java/org/investpro/risk/TradeRiskContext.java`
**Must include methods**:
```java
double calculateTradeRisk()
double calculateTradeReward()
double getPortfolioHeatPercent()
double getCumulativeRiskUsageRatio()
boolean isValidForRiskEvaluation()
boolean hasBracketRiskPlan()
String getSymbolText()
TradePair getTradePair()
```

### 10. ExchangeCapabilities Record
**File**: Create or verify `src/main/java/org/investpro/exchange/ExchangeCapabilities.java`
**Must be**:
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

### 11. TradingWindow UI-Only Stream
**File**: `src/main/java/org/investpro/investpro/ui/TradingWindow.java`
**Must implement**:
```java
void startDesktopStream(TradePair symbol);
void stopDesktopStream();
void updateTickerFromStream(Ticker ticker);
void updateOrderBookFromStream(OrderBook orderBook);
void updateAccountFromStream(Account account);
```

**Must NOT**:
- Contain strategy logic
- Evaluate risk
- Make AI decisions

### 12. DesktopExchangeStreamBridge Threading
**File**: `src/main/java/org/investpro/ui/DesktopExchangeStreamBridge.java`
**Must**:
- Stream on background thread
- UI updates via `Platform.runLater()`
- Never mutate ObservableList outside FX thread

---

## 🟢 LOWER PRIORITY (Phase 3+)

### 13. Agent Behavior Implementation
- [ ] MarketDataAgent: Normalize market events
- [ ] SignalAgent: Call StrategyEngine, publish SIGNAL_CREATED
- [ ] RiskAgent: Call RiskManagementSystem, publish RISK_APPROVED/REJECTED
- [ ] PortfolioAgent: Monitor exposure, publish warnings
- [ ] PositionManagementAgent: Create position intents
- [ ] ExecutionAgent: Call TradeExecutionCoordinator
- [ ] AuditAgent: Log all events to repository

### 14. NewsDataProvider MAJOR_INDICATORS
- [ ] Activate MAJOR_INDICATORS
- [ ] Add methods: isMajorIndicator(), getUpcomingMajorEvents()
- [ ] Enforce pair-level blackout during events
- [ ] Create NewsAgent if needed

### 15. Trade Persistence
- [ ] Complete Trade model
- [ ] Implement TradeRepositoryImpl
- [ ] Implement TradeService
- [ ] Support analytics (win rate, profit, etc.)

### 16. Strategy Research
- [ ] Create StrategyRegistry
- [ ] Create StrategySelectionService
- [ ] Implement backtesting for multiple timeframes
- [ ] Auto-select best strategy per symbol

---

## Verification Checklist

### Compilation
- [ ] No errors
- [ ] No warnings about missing methods
- [ ] No Java 21-only methods (getFirst, addFirst, removeLast)
- [ ] No primitive double compared with null

### Execution Safety
- [ ] FinalRiskGate consulted before every order
- [ ] No order reaches Exchange directly
- [ ] All orders logged with approval reason
- [ ] Rejected orders never executed

### UI/Bot Separation
- [ ] TradingWindow works without bot
- [ ] Bot works without UI
- [ ] Streaming lifecycle correct (bot on/off)
- [ ] No duplicate streams

### Agent Testing
- [ ] Agents can be tested in isolation
- [ ] No JavaFX dependencies in agents
- [ ] All agent failures logged without crashing

### Thread Safety
- [ ] All UI updates via Platform.runLater()
- [ ] No ObservableList mutations outside FX thread
- [ ] No blocking I/O on FX thread
- [ ] Proper executor service usage

---

## Testing Examples

### Test 1: Agent Startup Without UI
```java
@Test
void testAgentStartupWithoutUI() {
    AgentRegistry registry = new AgentRegistry();
    AgentEventBus eventBus = new AgentEventBus();
    AgentRuntime runtime = new AgentRuntime(registry, eventBus);
    
    MarketDataAgent agent = new MarketDataAgent();
    registry.register(agent);
    
    // Should start without JavaFX
    runtime.start(context);
    assertTrue(runtime.isStarted());
}
```

### Test 2: Order Requires FinalRiskGate Approval
```java
@Test
void testOrderRequiresApproval() {
    TradeRiskContext context = new TradeRiskContext();
    context.setPortfolioHeat(95.0); // At limit
    
    FinalRiskGate gate = new FinalRiskGate();
    FinalRiskGate.OrderApprovalResult result = gate.makeDecision(context);
    
    assertEquals(OrderApprovalDecision.REJECTED, result.getDecision());
    // Order never reaches Exchange
}
```

### Test 3: Streaming Lifecycle
```java
@Test
void testStreamingLifecycle() {
    // Bot OFF: Desktop stream active
    window.startDesktopStream(pair);
    assertTrue(window.isDesktopStreamActive());
    
    // Bot starts: Desktop stream stops
    core.startBot(tradingService, pair);
    assertFalse(window.isDesktopStreamActive());
    
    // Bot stops: Desktop stream resumes
    core.stopBot();
    assertTrue(window.isDesktopStreamActive());
}
```

---

## How to Proceed

1. **Read** `ARCHITECTURE.md` completely
2. **Fix** critical issues in priority order
3. **Compile** frequently (after each phase)
4. **Test** agent isolation
5. **Verify** execution pipeline
6. **Document** any deviations

