# InvestPro Trading Pipeline

InvestPro is moving toward an event-driven trading architecture where each stage has a clear responsibility.

```text
Market Data
  -> Market Context
  -> Indicators
  -> Strategy Signals
  -> Signal Aggregator
  -> Bot Decision
  -> Risk Decision
  -> Execution Guard
  -> Order Router
  -> Exchange / Broker
  -> Broker Activity Events
  -> Event Store
  -> Projections
  -> Reconciliation
  -> System Operations Snapshot
```

## Core Rule

A signal is not a trade.

A decision is not a trade.

An order request is not a trade.

A submitted order is not a trade.

Only a broker-confirmed fill is a real trade.

## Source Of Truth

Broker activity events are the durable source of truth for order and trade state. `ORDER_SUBMITTED` means InvestPro attempted to submit an order. It does not create a trade. A trade projection is created only from broker-confirmed fill events such as `ORDER_FILLED`.

## Strategy Boundary

Strategies consume `MarketContext` and produce `StrategySignal`. Strategies do not call exchange APIs, approve trades, size positions, or submit orders.

## Risk And Execution Boundary

The risk engine must approve every possible trade before routing. The execution guard performs final runtime checks such as stale data, stale account state, disconnected exchange, unhealthy stream, duplicate pending orders, and unsupported order types.

## Paper vs Live

Paper trading should be used before live execution. Live trading must remain behind risk decisions, execution guard checks, exchange capability validation, and broker reconciliation.
