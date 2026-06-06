# InvestPro Terminal Stage 1 Architecture

Stage 1 adds a non-disruptive foundation under `org.investpro.terminal`.

## Package Map

- `org.investpro.terminal.domain`: immutable terminal domain records for instruments, assets, candles, order books, broker activity, execution plans, risk decisions, strategy output, and backtest results.
- `org.investpro.terminal.provider`: provider contracts for market data, trading, accounts, instruments, broker activity, historical data, news, and analytics.
- `org.investpro.terminal.config`: typed configuration records loaded from existing `AppConfig` using `investpro.*` keys.
- `org.investpro.terminal.instrument`: `InstrumentMasterService` and reversible pair relationships.
- `org.investpro.terminal.persistence`: repository abstractions for instruments, broker activity, candles, strategy results, settings, and workspace layouts.
- `org.investpro.terminal.stellar`: issuer-aware Stellar metadata, trustline, liquidity, and pair-resolution scaffolding.
- `org.investpro.terminal.ai`: optional AI hook interfaces, disabled by configuration by default.
- `org.investpro.terminal.licensing`: feature-gate and license-tier placeholders.

## Existing Codebase Scan

- Market data: `org.investpro.market`, `org.investpro.exchange.contracts.MarketDataProvider`, exchange-specific candle suppliers.
- Trading/execution: `org.investpro.execution`, `org.investpro.core.agents.execution`, `org.investpro.exchange.execution`.
- Strategies: `org.investpro.strategy`, `org.investpro.strategy.lab`, `org.investpro.strategy.management`.
- Charting: `org.investpro.ui.charts`.
- Config/settings: `org.investpro.config`, `src/main/resources/config.properties`, UI settings panels.
- Exchange adapters: `org.investpro.exchange`, `org.investpro.exchange.coinbase`, `org.investpro.exchange.oanda`, `org.investpro.exchange.ibkr`, provider classes under `org.investpro.exchange.providers`.
- UI panels: `org.investpro.ui`, `org.investpro.ui.panels`, `org.investpro.ui.docking`.

## Configuration Keys

Prefer these new keys over older `tradeadviser.*` names:

- `investpro.risk.requireBacktestBeforeLive=true`
- `investpro.risk.requirePaperTradingBeforeLive=true`
- `investpro.risk.autoAssignBestStrategy=false`
- `investpro.risk.minStrategyScore=70`
- `investpro.risk.topCandidates=5`
- `investpro.risk.maxDailyLossPercent=3`
- `investpro.risk.maxPositionSizePercent=10`
- `investpro.risk.maxPortfolioExposurePercent=80`
- `investpro.risk.maxSpreadPercent=0.30`
- `investpro.risk.closeForexBeforeWeekend=true`
- `investpro.risk.smallAccountThreshold=100`
- `investpro.risk.smallAccountMaxUnits=1`
- `investpro.ai.enabled=false`
- `investpro.ai.host=127.0.0.1`
- `investpro.ai.port=8010`
- `investpro.ai.timeoutMs=1500`
- `investpro.ai.remoteEnabled=false`
- `investpro.ai.minConfidence=0.60`
- `investpro.ai.highConfidence=0.80`

## Migration Notes

Stage 1 does not replace existing adapters or UI services. Existing classes can progressively implement `org.investpro.terminal.provider.*` interfaces in Stage 2.

Broker/exchange activity remains the source of truth. The new `BrokerActivityEvent` and `BrokerActivityRepository` contracts are meant to converge with the existing `org.investpro.activity` persistence pipeline during Stage 3.

## Run

Compile:

```powershell
.\mvnw.cmd -DskipTests compile
```

Clean compile after large JavaFX edits:

```powershell
.\mvnw.cmd clean compile -DskipTests
```

## TODO

- Stage 2: adapter bridges for Coinbase, OANDA, IBKR, Alpaca, BinanceUS, and Stellar.
- Stage 3: projection services and reconciliation using the new terminal activity contracts.
- Stage 4: JavaFX panels should observe projections rather than own business state.
- Stage 5: backtest executor, strategy ranking persistence, AI gRPC client implementations, and full feature-gate wiring.
