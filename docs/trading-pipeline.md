# Trading Decision Pipeline

## Core Principle

A signal is not a trade.
A decision is not a trade.
An order intent is not a trade.
Only a broker-confirmed fill is a real trade.

## Decision Flow

1. Build `MarketContext` and `SignalDecision`.
2. Run `TickerValidationService` and `SpreadValidationService`.
3. Detect asset class with `AssetMarketTypeDetector`.
4. Infer market regime using `MarketRegimeAnalyzer`.
5. Score strategy fitness using `StrategyFitScoringService`.
6. Fall back to `IndicatorSetupScoringService` when strategy fit is weak.
7. Build a `TradePlan` with `TradePlanGenerator`.
8. Estimate costs with `TradeCostEstimator`.
9. Estimate expectancy with `TradeExpectationEstimator`.
10. Validate account constraints with `AccountTradeValidator`.
11. Produce a `BotTradeDecision` action (`TRADE`, `SKIP`, `WAIT`, `HOLD`, `REDUCE_SIZE`, `CLOSE`).

## Execution Boundary

- `BotTradeDecisionEngine` only evaluates and decides.
- `OrderIntent` only expresses an intent to submit an order.
- `ExecutionGuard` and `RiskEngine` only approve or block downstream actions.
- Broker activity events are tracked separately in `org.investpro.broker.*`.

## Broker Event Semantics

`BrokerActivityType.ORDER_SUBMITTED` means an order was sent.
`BrokerActivityType.ORDER_FILLED` means the broker confirmed a fill.

Projection, PnL, and position state should be built from fill events, not submission events.
