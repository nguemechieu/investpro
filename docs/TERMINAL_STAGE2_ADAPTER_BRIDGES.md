# InvestPro Terminal Stage 2: Provider Adapter Bridges

Stage 2 introduces terminal-facing adapter bridges for the existing exchange stack. The goal is to expose Coinbase, OANDA, Stellar, and the other current `Exchange` implementations through the normalized terminal provider contracts added in Stage 1 without replacing the working exchange adapters in one risky step.

## What Changed

- `org.investpro.terminal.adapter.TerminalExchangeMapper`
  - Converts legacy `TradePair`, `Ticker`, `OrderBook`, `CandleData`, `Account`, `OpenOrder`, and `Position` models into terminal domain records.
  - Normalizes provider instrument ids as `providerId + symbol + nativeSymbol`.
  - Keeps placeholder candles visible to the terminal charting layer, which matters for sparse Stellar candles.

- `org.investpro.terminal.adapter.ExchangeTerminalProviderAdapter`
  - Wraps any existing `Exchange`.
  - Implements terminal `ProviderBundle`, `MarketDataProvider`, `HistoricalDataProvider`, `TradingProvider`, `AccountProvider`, and `InstrumentProvider`.
  - Discovers instruments from `exchange.getTradablePairs()`.
  - Fetches live ticks, order books, historical candles, account balances, and positions through existing exchange methods.
  - Delegates live/paper order submission only when an `ExecutionPlan` contains an allowed `RiskDecision`.

- `org.investpro.terminal.adapter.StellarTerminalProviderAdapter`
  - Wraps `StellarNetwork`.
  - Resolves issuer-aware Stellar pairs through `resolvePairIdentity`.
  - Evaluates direct/inverted liquidity through `evaluatePairWithInversion`.
  - Emits terminal instruments with Stellar metadata:
    - `stellar.baseIssuer`
    - `stellar.quoteIssuer`
    - `stellar.baseCanonicalKey`
    - `stellar.quoteCanonicalKey`
    - `stellar.baseTrustlineRequired`
    - `stellar.quoteTrustlineRequired`
    - `stellar.usingInvertedOrderBook`
    - `stellar.liquidityReason`
    - `stellar.marketWatchEligible`

- `org.investpro.terminal.adapter.TerminalProviderAdapterFactory`
  - Returns a Stellar-specific adapter for `StellarNetwork`.
  - Returns the generic adapter for all other exchanges.

## Live Trading Behavior

The adapter bridge does not bypass the safety model. Terminal order submission requires:

1. A non-null `ExecutionPlan`.
2. A non-null `OrderRequest`.
3. A non-null `RiskDecision` with `allowed=true`.
4. Existing exchange-level trading capability checks inside the wrapped exchange.

This keeps Stage 2 compatible with the current bot/runtime while preparing the terminal execution layer for a dedicated risk and routing engine in the next stage.

## Stellar Market Watch Requirement

Trusted issuer pairs now flow through two paths:

- Existing desktop Market Watch discovery from `StellarNetwork`.
- Terminal instrument discovery through `StellarTerminalProviderAdapter`.

That means trusted issuer symbols such as `BTCLN/XLM`, `BTC/USDC`, or user-added trustline assets can be represented as issuer-aware terminal instruments and marked as Market Watch eligible for live trading workflows.

## Migration Notes

Use the factory when wiring terminal provider surfaces:

```java
ProviderBundle terminalProvider = TerminalProviderAdapterFactory.forExchange(exchange);
```

The old exchange adapters remain the source of truth during Stage 2. The terminal package now provides the normalized surface needed for Bloomberg-style dashboards, consolidated watchlists, AI ranking, broker activity, and staged execution.
