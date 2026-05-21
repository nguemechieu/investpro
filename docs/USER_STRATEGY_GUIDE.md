# InvestPro User Strategy Guide

InvestPro separates market opinion from execution. A user signal or strategy never places orders directly. It returns a `TradingSignal` or `StrategyDecision`; the platform then routes that decision through:

`SignalProvider / InvestProStrategy -> SignalFusionEngine -> StrategyDecision -> BehaviourGuardConfig -> RiskEngine -> ExecutionEngine`

This keeps backtesting, paper trading, and live trading behind the same safety gates.

## Signals

A signal is a small market-opinion plugin. It implements `org.investpro.strategy.api.SignalProvider`:

```java
public interface SignalProvider {
    SignalMetadata metadata();
    TradingSignal evaluate(SignalContext context);
}
```

Signals inspect the `SignalContext` and return `BUY`, `SELL`, `HOLD`, `CLOSE`, or `REDUCE`. If no setup exists, return `TradingSignal.hold(...)`.

## Strategies

`InvestProStrategy` is a tick/candle-level strategy contract. It can maintain lifecycle state with `onStart` and `onStop`, but still returns decisions only.

`CompositeStrategy` combines multiple signal outputs. It is ideal for weighted voting, confidence fusion, and no-code JSON strategies.

## JSON Signal Example

Place JSON signal files in:

`~/InvestPro/strategies/json`

```json
{
  "type": "SIGNAL",
  "id": "rsi-oversold-signal",
  "name": "RSI Oversold Signal",
  "version": "1.0.0",
  "author": "User",
  "weight": 1.2,
  "requiredIndicators": ["RSI_14"],
  "supportedTimeframes": ["5m", "15m", "1h"],
  "rules": [
    {
      "when": "RSI_14 < 30",
      "action": "BUY",
      "confidence": 0.70,
      "reason": "RSI is oversold"
    },
    {
      "when": "RSI_14 > 70",
      "action": "SELL",
      "confidence": 0.70,
      "reason": "RSI is overbought"
    }
  ]
}
```

Rule expressions support `>`, `>=`, `<`, `<=`, `==`, `!=`, plus `AND` and `OR`. They are evaluated by InvestPro's safe expression evaluator, not by a script engine.

## JSON Composite Strategy Example

```json
{
  "type": "COMPOSITE_STRATEGY",
  "id": "user-combo-strategy",
  "name": "User Combo Strategy",
  "version": "1.0.0",
  "timeframes": ["5m", "15m"],
  "fusion": {
    "method": "WEIGHTED_VOTE",
    "minimumConfidence": 0.65
  },
  "signals": [
    { "id": "ema-trend-signal", "weight": 1.5, "enabled": true },
    { "id": "rsi-oversold-signal", "weight": 1.0, "enabled": true },
    { "id": "macd-cross-signal", "weight": 1.3, "enabled": true }
  ],
  "risk": {
    "riskPercent": 0.5,
    "stopLossAtrMultiplier": 1.5,
    "takeProfitAtrMultiplier": 2.2
  }
}
```

## Java Plugin Example

Create a public class with a public no-args constructor:

```java
public class EmaRsiSignal implements SignalProvider {
    public SignalMetadata metadata() { ... }
    public TradingSignal evaluate(SignalContext context) { ... }
}
```

Package compiled classes into a `.jar` and place it in:

`~/InvestPro/strategies/jars`

InvestPro scans JARs for classes implementing:

- `SignalProvider`
- `InvestProStrategy`
- `CompositeStrategy`

Optional metadata file inside the JAR:

`investpro-strategy.json`

## Built-In Signals

InvestPro registers these built-ins automatically:

- `ema-trend-signal`
- `rsi-oversold-signal`
- `macd-cross-signal`
- `vwap-deviation-signal`

## Folder Structure

```text
~/InvestPro/strategies/
  jars/
  json/
  disabled/
  failed/
```

Move a file to `disabled/` to keep it installed but inactive. Failed or unsafe plugins can be moved to `failed/` by platform tooling.

## Safety Rules

- Signals and strategies must not place orders.
- Signals and strategies must not call broker APIs directly.
- Return `HOLD` when uncertain or when inputs are missing.
- Throwing from a plugin will not crash InvestPro; the runtime catches errors, logs them, and continues.
- Live execution is always gated by behavior controls, risk checks, and the execution engine.

## Symbol Availability Rules

InvestPro applies universal symbol tradability checks before symbol lists are used by each workflow.

- **UI browsing / market data views:** symbols with `marketDataAllowed=true`.
- **Backtesting:** symbols with `marketDataAllowed=true`, even if live trading is disabled for that symbol.
- **Bot trading:** symbols with `botTradingAllowed=true` only.
- **Live order submission:** revalidated at submit time and requires `orderSubmissionAllowed=true`.

This means a strategy can be valid for research and backtesting while still being blocked from live execution until the venue allows order submission for that symbol.
