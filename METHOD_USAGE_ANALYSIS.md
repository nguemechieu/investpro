# Method Usage Analysis - InvestPro Exchange Interface

> Historical report: retained for its original revision. Java versions, test counts, completion claims and code examples below are historical, not current setup or release evidence. For the JDK 27 / JavaFX 27 baseline, use the [project README](README.md) and [documentation index](docs/README.md). Classified on 2026-10-05.

## Summary
Analysis of 40 methods from the Exchange interface to identify which are actually called/used in src/main/java (excluding test files).

**Total methods analyzed:** 40  
**Methods actually used (called from code):** 4  
**Methods never referenced:** 36  

---

## METHODS ACTUALLY USED

### 1. **submitOrder()**
- **Status:** ✅ USED
- **Called from:**
  - ManualTradePanel.java (historical file: `ManualTradePanel.java`) line 340
  - ManualTradePanel.java (historical file: `ManualTradePanel.java`) line 341
- **Context:** 
  ```java
  buyButton.setOnAction(event -> submitOrder(Side.BUY));
  sellButton.setOnAction(event -> submitOrder(Side.SELL));
  ```

### 2. **getLivePrice(TradePair tradePair)**
- **Status:** ✅ USED
- **Called from:**
  - [SignalProcessor.java](src/main/java/org/investpro/exchange/infrastructure/SignalProcessor.java) line 149 - `ticker = exchange.getLivePrice(symbol);`
  - [PollingExchangeStreamer.java](src/main/java/org/investpro/exchange/infrastructure/PollingExchangeStreamer.java) line 47 - `exchange.getLivePrice(pair)`
- **Context:** Used for fetching current ticker data for trading signals and streaming

### 3. **fetchAccountTrades(TradePair)**
- **Status:** ✅ USED (partially)
- **Called from:**
  - TradingWindow.java (historical file: `TradingWindow.java`) line 3096 - `exchange.fetchAccountTrades(null)`
- **Note:** Called with null parameter

### 4. **fetchPositions(TradePair)**
- **Status:** ⚠️ USED (internally only)
- **Called from:**
  - [Coinbase.java](src/main/java/org/investpro/exchange/coinbase/Coinbase.java) line 1630 - Internal call within `fetchPosition()` method
  - [Oanda.java](src/main/java/org/investpro/exchange/oanda/Oanda.java) line 1361 - Internal call within another method
- **Context:** Called within exchange implementations, not from external UI/service code

---

## METHODS NEVER USED

### WebSocket/Streaming Support Methods (10 methods)
- ❌ `supportsWebSocket()`
- ❌ `isWebsocketAvailable()`
- ❌ `supportsNativeWebSocket()`
- ❌ `supportsHttpStreaming()`
- ❌ `supportsPollingFallback()`
- ❌ `isStreamConnected()`
- ❌ `reconnectStream()`
- ❌ `supportsAccountStreaming()`
- ❌ `supportsOrderStreaming()`
- ❌ `supportsFillStreaming()`

### Streaming Subscription Methods (5 methods)
- ❌ `subscribeToOrderUpdates(TradePair, OrderCommandConsumer)`
- ❌ `subscribeToBalance(ExchangeStreamConsumer)`
- ❌ `subscribeToCandles(TradePair, ExchangeStreamConsumer)`
- ❌ `subscribeToTrades(TradePair, ExchangeStreamConsumer)`
- ❌ `fetchTrades(TradePair)`

### Position Streaming Methods (4 methods)
- ❌ `supportsPositionStreaming()`
- ❌ `supportsBalanceStreaming()`
- ❌ `supportsTickerStreaming()`
- ❌ `supportsCandleStreaming()`
- ❌ `supportsTradeStreaming()`

### Market Data Methods (2 methods)
- ❌ `getTradablePairs()`
- ❌ `supportsTradePair(TradePair)`
- ❌ `getTicker(TradePair)` - parameter version
- ❌ `getLivePrice()` - no-parameter version (parameter version IS used)

### Order & Leverage Methods (6 methods)
- ❌ `fetchOrder(String orderId)`
- ❌ `fetchAccountTradesSince(TradePair, Instant)`
- ❌ `fetchAccountTradesBetween(TradePair, Instant, Instant)`
- ❌ `getMinOrderNotional(TradePair)`
- ❌ `fetchLeverage(TradePair)`
- ❌ `setLeverage(TradePair, double)` - Note: `botConfig.setLeverage()` is called, but it's NOT the exchange method

### Capability Check Methods (7 methods)
- ❌ `supportsPositions()`
- ❌ `supportsAccountTrades()`
- ❌ `supportsLeverage()`
- ❌ `supportsDerivatives()`
- ❌ `supportsStocks()`

### Async/Logging Methods (2 methods)
- ❌ `createOrderAsync(Order, OrderCommandConsumer)`
- ❌ `logExchangeProperties()`
- ❌ `canSubmitLiveOrders()`

---

## Code Locations of Actual Usage

### [SignalProcessor.java](src/main/java/org/investpro/exchange/infrastructure/SignalProcessor.java)
```java
try {
    ticker = exchange.getLivePrice(symbol);  // LINE 149
} catch (Exception e) {
    log.warn("Could not fetch ticker for {}: {}", symbol, e.getMessage());
    executeFallbackTrade(type, symbol);
    return;
}
```

### [PollingExchangeStreamer.java](src/main/java/org/investpro/exchange/infrastructure/PollingExchangeStreamer.java)
```java
public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
    tickerTasks.computeIfAbsent(tradePair, pair -> scheduleAtFixedRate(() -> {
        try {
            consumer.onTicker(exchange.getName(), pair, exchange.getLivePrice(pair));  // LINE 47
        } catch (Exception exception) {
            consumer.onError(exchange.getName(), exception);
        }
    }, 1));
}
```

### TradingWindow.java (historical file: `TradingWindow.java`)
```java
exchange.fetchAccountTrades(null)  // LINE 3096
```

### ManualTradePanel.java (historical file: `ManualTradePanel.java`)
```java
buyButton.setOnAction(event -> submitOrder(Side.BUY));      // LINE 340
sellButton.setOnAction(event -> submitOrder(Side.SELL));    // LINE 341
```

---

## Implications

### Dead Code Concerns
The vast majority of exchange interface methods (36 out of 40) are **never called** in the actual codebase. This suggests:

1. **Over-engineered interface** - The Exchange interface defines many streaming and capability methods that are not currently used
2. **Potential for cleanup** - Dead code should be identified and either:
   - Removed if truly unused and not needed for future functionality
   - Documented if they represent planned features
3. **Implementation burden** - Every new exchange adapter must implement all these unused methods

### Active Methods
Only 4 methods are actively used:
- **submitOrder()** - Manual trading in UI
- **getLivePrice(TradePair)** - Price lookup for signals and streaming
- **fetchAccountTrades()** - Account history retrieval (with null parameter)
- **fetchPositions()** - Internal use in exchange adapters only

### Recommendations
1. Review if streaming methods (`subscribeToOrderUpdates`, `subscribeToBalance`, etc.) should be removed or if they represent future functionality
2. Review capability checker methods - these could be useful for dynamic UI/logic but are currently hardcoded
3. Consider creating a minimal "core" exchange interface with only actively used methods
4. Document why methods like `getTradablePairs()`, `supportsPositions()`, etc. exist but aren't used
