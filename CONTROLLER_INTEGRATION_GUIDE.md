# Controller Integration Guide - How to Use

## Quick Start: Using the New Controllers

### 1. BotRuntimeController - For SystemCore

Replace this:
```java
// OLD: Direct SmartBot manipulation
smartBot.start(exchange, tradingService, pair);
smartBot.setAutoTradingEnabled(true);
smartBot.setAiReasoningEnabled(true);
```

With this:
```java
// NEW: Through controller layer
BotRuntimeController botController = new BotRuntimeController(smartBot);
botController.start(exchange, tradingService, pair);
botController.setAutoTradingEnabled(true);
botController.setAiReasoningEnabled(true);
```

**Benefits:**
- Consistent error handling with try-catch
- Logging of all operations
- Single point of control
- Easy to add validation later

---

### 2. TradeDecisionPipeline - For Risk Evaluation

All trades flow through ONE place:
```java
TradeDecisionPipeline pipeline = new TradeDecisionPipeline(riskManagementSystem);

// Build context from signal or manual input
TradeRiskContext context = TradeRiskContextBuilder
    .fromSignal("BTC/USDT", 1.0, 29000.0, 28000.0, 30000.0, 
                exchange, account);

// Evaluate
RiskDecision decision = pipeline.evaluate(context);

// Check result
if (decision.isApproved()) {
    // Execute trade
    executionEngine.execute(decision.getFinalPositionSize(), 
                           decision.getFinalLeverage());
} else {
    // Show user why rejected
    String reason = pipeline.getApprovalReason(decision);
    ui.showError("Trade rejected: " + reason);
}
```

**Benefits:**
- Single source of truth for risk approval
- No bypassing risk evaluation
- Clear approval/rejection reason
- Automatic logging

---

### 3. ManualTradeController - For UI Button Clicks

Replace this:
```java
// OLD: Complex UI button logic
private void onBuyButtonClick(String symbol, double qty) {
    try {
        // ... manual trade execution logic ...
        Order order = exchange.placeOrder(symbol, "BUY", qty);
        updateUI(order);
    } catch (Exception e) {
        showError("Trade failed: " + e.getMessage());
    }
}
```

With this:
```java
// NEW: Simple delegation
private void onBuyButtonClick(String symbol, double qty) {
    manualTradeController.executeBuy(symbol, qty)
        .thenAccept(order -> {
            log.info("Order placed: {}", order.getOrderId());
            updateUI(order);
        })
        .exceptionally(error -> {
            log.error("Trade failed: {}", error.getMessage());
            showError("Trade failed: " + error.getMessage());
            return null;
        });
}
```

**Benefits:**
- Async execution (UI doesn't block)
- Risk evaluation automatic
- Consistent error handling
- Easy to add logging/telemetry

---

## Complete Example: SystemCore Integration

```java
public class SystemCore {
    private final SmartBot smartBot;
    private final BotRuntimeController botController;
    private final ManualTradeController manualTradeController;
    private final TradeDecisionPipeline tradeDecisionPipeline;
    
    public SystemCore(Exchange exchange, Properties config) {
        // Create SmartBot
        this.smartBot = new SmartBot(
            new AgentRuntime(),
            new AgentEventBus(),
            new AgentRegistry()
        );
        
        // Wrap with controllers
        this.botController = new BotRuntimeController(smartBot);
        this.tradeDecisionPipeline = new TradeDecisionPipeline(riskManagementSystem);
        this.manualTradeController = new ManualTradeController(
            tradeDecisionPipeline,
            orderExecutionService,
            exchange,
            tradingService
        );
    }
    
    // Start bot with clean API
    public void start(TradingService tradingService, TradePair pair) {
        botController.start(exchange, tradingService, pair);
        log.info("SystemCore: Bot started");
    }
    
    // Stop bot cleanly
    public void stop() {
        botController.stop();
        log.info("SystemCore: Bot stopped");
    }
    
    // Enable/disable auto-trading
    public void setAutoTradingEnabled(boolean enabled) {
        botController.setAutoTradingEnabled(enabled);
    }
    
    // Get bot controller for UI
    public BotRuntimeController getBotController() {
        return botController;
    }
    
    // Get manual trade controller for UI
    public ManualTradeController getManualTradeController() {
        return manualTradeController;
    }
}
```

---

## Complete Example: TradingWindow Integration

```java
public class TradingWindow extends JFrame {
    private SystemCore systemCore;
    private BotRuntimeController botController;
    private ManualTradeController manualTradeController;
    
    private void onStartBotButton() {
        botController.start(exchange, tradingService, selectedPair);
        updateStatusLabel("Bot started");
    }
    
    private void onStopBotButton() {
        botController.stop();
        updateStatusLabel("Bot stopped");
    }
    
    private void onAutoTradingCheckbox(boolean checked) {
        botController.setAutoTradingEnabled(checked);
        updateStatusLabel(checked ? "Auto-trading enabled" : "Auto-trading disabled");
    }
    
    private void onBuyButton(double quantity) {
        manualTradeController.executeBuy(selectedSymbol, quantity)
            .thenAccept(order -> {
                log.info("BUY order placed: {}", order.getOrderId());
                updateOrdersTable(order);
                showNotification("Buy order executed: " + order.getOrderId());
            })
            .exceptionally(error -> {
                log.error("BUY failed", error);
                showErrorDialog("Buy failed: " + error.getMessage());
                return null;
            });
    }
    
    private void onSellButton(double quantity) {
        manualTradeController.executeSell(selectedSymbol, quantity)
            .thenAccept(order -> {
                log.info("SELL order placed: {}", order.getOrderId());
                updateOrdersTable(order);
                showNotification("Sell order executed: " + order.getOrderId());
            })
            .exceptionally(error -> {
                log.error("SELL failed", error);
                showErrorDialog("Sell failed: " + error.getMessage());
                return null;
            });
    }
    
    private void onCloseButton() {
        manualTradeController.executeClose(selectedSymbol)
            .thenAccept(order -> {
                log.info("Position closed: {}", order.getOrderId());
                updateOrdersTable(order);
                showNotification("Position closed: " + order.getOrderId());
            })
            .exceptionally(error -> {
                log.error("CLOSE failed", error);
                showErrorDialog("Close failed: " + error.getMessage());
                return null;
            });
    }
    
    // Constructor
    public TradingWindow(SystemCore systemCore) {
        this.systemCore = systemCore;
        this.botController = systemCore.getBotController();
        this.manualTradeController = systemCore.getManualTradeController();
        
        // Setup UI components...
    }
}
```

---

## Testing the Controllers

### Unit Test Example

```java
@Test
public void testBotRuntimeControllerStart() {
    SmartBot smartBot = mock(SmartBot.class);
    BotRuntimeController controller = new BotRuntimeController(smartBot);
    
    Exchange exchange = mock(Exchange.class);
    TradingService service = mock(TradingService.class);
    TradePair pair = mock(TradePair.class);
    
    controller.start(exchange, service, pair);
    
    verify(smartBot).start(exchange, service, pair);
}

@Test
public void testManualTradeRejectedByRisk() {
    RiskDecision rejection = RiskDecision.rejected("Insufficient margin");
    TradeDecisionPipeline pipeline = mock(TradeDecisionPipeline.class);
    when(pipeline.evaluate(any())).thenReturn(rejection);
    
    OrderExecutionService execution = mock(OrderExecutionService.class);
    Exchange exchange = mock(Exchange.class);
    TradingService tradingService = mock(TradingService.class);
    
    ManualTradeController controller = new ManualTradeController(
        pipeline, execution, exchange, tradingService
    );
    
    // Execute should fail if risk rejected
    CompletableFuture<OpenOrder> result = controller.executeBuy("BTC/USDT", 1.0);
    
    // Should complete exceptionally (rejected)
    assertThrows(ExecutionException.class, () -> result.get());
    
    // Execution should NOT be called
    verify(execution, never()).executeOrder(any(), any(), anyDouble(), anyDouble());
}
```

---

## Migration Checklist

- [ ] Create BotRuntimeController instance in SystemCore
- [ ] Create TradeDecisionPipeline instance in SystemCore  
- [ ] Create ManualTradeController instance in SystemCore
- [ ] Update SystemCore.start() to use botController
- [ ] Update SystemCore.stop() to use botController
- [ ] Update TradingWindow to get controllers from SystemCore
- [ ] Update TradingWindow button handlers to use manualTradeController
- [ ] Add logging to track all trade decisions
- [ ] Add unit tests for all controller paths
- [ ] Test end-to-end: UI → Controller → Risk → Execution → Exchange

---

## Key Design Rules Enforced

✅ **ALL trades go through risk evaluation**
- No direct ExecutionEngine calls
- No bypassing RiskManagementSystem

✅ **SmartBot remains runtime-only**
- No trade execution logic
- No risk evaluation logic
- Focus: manage agents, publish events

✅ **Clear separation of concerns**
- Controllers: lifecycle + manual trades
- Pipeline: risk orchestration
- RiskEngine: approval decisions
- ExecutionEngine: order placement

✅ **Async execution**
- UI doesn't block on trade execution
- Errors properly propagated
- Events inform UI of completion

