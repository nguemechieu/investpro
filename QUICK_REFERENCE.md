# InvestPro Refactoring - Quick Reference Card

## What's Been Done ✅

### Documentation Created
- **ARCHITECTURE.md** (400+ lines): Complete specification of all 8 layers
- **STATUS.md**: Current component status and validation checklist
- **REFACTORING_CHECKLIST.md**: Priority-ordered tasks (🔴 CRITICAL to 🟢 LOWER)
- **validate-refactoring.bat**: Compilation validation script

### Code Status Review ✅
- ✅ **SmartBot.isStarted()**: CORRECT - returns started.get()
- ✅ **TradeExecutionCoordinator**: FULL PIPELINE implemented (Signal → Risk → AI → FinalGate → Execution)
- ✅ **ExecutionEngine**: Complete with symbol filtering and multiple order types
- ✅ **SystemCore**: Full composition root owning all components
- ✅ **Agent Framework**: Complete with registry, event bus, runtime
- ✅ **AI Layer**: All interfaces and classes in place
- ⚠️ **Agent Behaviors**: Exist but need verification of non-stub implementations
- ⚠️ **UI/Bot Streaming**: Exists but lifecycle needs verification
- 📋 **TradeRiskContext**: Needs verification of all methods
- 📋 **Persistence Layer**: May need completion

---

## What to Do Next - Priority Order

### 🔴 CRITICAL (Do These First)

**1. Run Compilation Validation** (5 min)
```bash
cd c:\Users\nguem\Documents\GitHub\investpro
mvnw clean compile
# Should have zero errors
```

**2. Verify Agent Behaviors** (1 hour)
```
Check files:
- src/main/java/org/investpro/core/agents/market/MarketDataAgent.java
- src/main/java/org/investpro/core/agents/signal/SignalAgent.java
- src/main/java/org/investpro/core/agents/risk/RiskAgent.java
- src/main/java/org/investpro/core/agents/portfolio/PortfolioAgent.java
- src/main/java/org/investpro/core/agents/execution/ExecutionAgent.java

Each should have:
✓ Non-empty onEvent() implementation
✓ Switch on event types
✓ Calls to appropriate services (StrategyEngine, RiskMgmt, ExecutionCoordinator)
✓ publishes events to eventBus
```

**3. Verify UI/Bot Streaming Separation** (1 hour)
```
Check files:
- src/main/java/org/investpro/ui/TradingWindow.java
- src/main/java/org/investpro/ui/DesktopExchangeStreamBridge.java

Verify:
✓ TradingWindow has startDesktopStream() / stopDesktopStream()
✓ DesktopExchangeStreamBridge uses Platform.runLater() for UI updates
✓ When bot starts, desktop stream stops
✓ When bot stops, desktop stream resumes
```

**4. Verify TradeRiskContext Completeness** (30 min)
```
Check file:
- src/main/java/org/investpro/risk/TradeRiskContext.java

Verify methods exist (from ARCHITECTURE.md):
✓ calculateTradeRisk(): double
✓ calculateTradeReward(): double
✓ getPortfolioHeatPercent(): double
✓ getCumulativeRiskUsageRatio(): double
✓ isValidForRiskEvaluation(): boolean
✓ getSymbolText(): String
✓ getTradePair(): TradePair
```

---

### 🟠 HIGH PRIORITY (After Verification)

**5. Add Missing Methods** (As Needed)
- If TradeRiskContext is missing methods, add safe implementations
- If agent behaviors are stubs, implement them
- If streaming lifecycle is broken, fix it

**6. Verify ExchangeCapabilities** (30 min)
```
Check file:
- src/main/java/org/investpro/exchange/ExchangeCapabilities.java

Should be immutable record with:
✓ marketData, accountData, orderSubmission
✓ spot, margin, futures, perpetuals, options
✓ hedging, stopLossModification, takeProfitModification
✓ trailingStop, websocket
```

**7. Run Full Integration Test** (1 hour)
```
Test end-to-end:
1. Bot OFF: Desktop stream active ✓
2. Bot starts: Desktop stream stops ✓
3. Market event flows: Ticker → Signal → Risk → AI → Execution ✓
4. Order execution: Blocked without FinalRiskGate approval ✓
5. Bot stops: Desktop stream resumes ✓
```

---

### 🟡 MEDIUM PRIORITY (Polish & Testing)

**8. Complete News Provider** (if needed)
- Implement MAJOR_INDICATORS detection
- Add methods for major event checking

**9. Complete Trade Persistence** (if needed)
- Complete Trade model
- Implement TradeRepositoryImpl
- Implement TradeService

**10. Add Documentation** (as development proceeds)
- Document agent event flows
- Document risk evaluation rules
- Document AI decision processes

---

## Key Files to Know

| Purpose            | File                                                                             | Status |
|--------------------|----------------------------------------------------------------------------------|--------|
| System Entry Point | src/main/java/org/investpro/app/InvestProApplication.java                        | ✅      |
| Composition Root   | src/main/java/org/investpro/core/SystemCore.java                                 | ✅      |
| Bot Runtime        | src/main/java/org/investpro/core/bot/SmartBot.java                               | ✅      |
| Execution Pipeline | src/main/java/org/investpro/core/agents/execution/TradeExecutionCoordinator.java | ✅      |
| Order Executor     | src/main/java/org/investpro/core/agents/execution/ExecutionEngine.java           | ✅      |
| Risk Context       | src/main/java/org/investpro/risk/TradeRiskContext.java                           | ⚠️     |
| AI Service         | src/main/java/org/investpro/ai/AiReasoningService.java                           | ✅      |
| Final Gate         | src/main/java/org/investpro/ai/FinalRiskGate.java                                | ✅      |
| Agent Registry     | src/main/java/org/investpro/core/agents/AgentRegistry.java                       | ✅      |
| Trading UI         | src/main/java/org/investpro/ui/TradingWindow.java                                | ⚠️     |
| Desktop Stream     | src/main/java/org/investpro/ui/DesktopExchangeStreamBridge.java                  | ⚠️     |
| Market Agent       | src/main/java/org/investpro/core/agents/market/MarketDataAgent.java              | ⚠️     |
| Signal Agent       | src/main/java/org/investpro/core/agents/signal/SignalAgent.java                  | ⚠️     |
| Risk Agent         | src/main/java/org/investpro/core/agents/risk/RiskAgent.java                      | ⚠️     |

---

## Testing Cheat Sheet

### Test 1: Can Compile?
```bash
mvnw clean compile
# Should have ZERO errors
```

### Test 2: Agent Isolation
```bash
# Can you start agents without JavaFX?
# Can you test SignalAgent without accessing Exchange?
# Verify no hardcoded dependencies
```

### Test 3: Order Approval Pipeline
```bash
# Try to execute order with:
# - portfolio heat = 96% (should REJECT)
# - cumulative risk = 99.5% (should REJECT)
# - Both valid (should APPROVE)
# Verify orders never bypass FinalRiskGate
```

### Test 4: Streaming Lifecycle
```bash
# 1. Start app (desktop stream ON)
# 2. Enable bot (desktop stream OFF, bot stream ON)
# 3. Disable bot (bot stream OFF, desktop stream ON)
# Verify no duplicate streams
```

---

## Red Flags to Avoid

❌ **Never do these:**
- Don't let agents call Exchange directly
- Don't execute orders without FinalRiskGate approval
- Don't use old list methods (getFirst, addFirst, removeLast) - use get(0), add(0, item), remove(size()-1)
- Don't mutate ObservableList outside Platform.runLater()
- Don't put strategy/risk/AI logic in TradingWindow
- Don't create duplicate streams (desktop + bot)
- Don't let AI directly execute orders
- Don't ignore compilation errors

✅ **Always do these:**
- Use Platform.runLater() for ALL UI updates
- Call FinalRiskGate before EVERY order
- Log all risk decisions and approvals
- Fail safely without crashing the system
- Keep agents testable in isolation
- Verify UI works without bot, bot works without UI

---

## Getting Help

1. **For architecture questions**: Read ARCHITECTURE.md
2. **For current status**: Read STATUS.md
3. **For next steps**: Read REFACTORING_CHECKLIST.md
4. **For compilation errors**: Run mvnw clean compile
5. **For design decisions**: Check ARCHITECTURE.md Principles section

---

## Success Criteria

When complete, you should be able to:
- [ ] Compile with zero errors
- [ ] Start bot without UI
- [ ] Run UI without bot
- [ ] Test agents without JavaFX
- [ ] Execute orders only through FinalRiskGate
- [ ] Switch bot on/off without stream collisions
- [ ] Scale to multiple symbols and strategies
- [ ] Add new agents without refactoring core
- [ ] Monitor system through agent events
- [ ] Understand entire architecture in one sitting

---

**Last Updated**: This refactoring session  
**Architecture Version**: v2.0 - Clean Separation, Protected Execution, Agent-Driven
**Status**: Foundation Complete, Implementation Verification In Progress ✅

