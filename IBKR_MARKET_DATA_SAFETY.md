# IBKR market-data safety

IBKR market data defaults to real broker data in both LIVE and PAPER execution modes. PAPER execution does not authorize simulated market data.

For an offline local demo, explicitly set the IBKR ExchangeCredentials parameter `marketDataSimulation=true`. This setting permits generated quotes, candles, depth and trades only while the connection manager is in PAPER mode. LIVE ignores the setting; an in-flight simulation result is discarded if the connection switches to LIVE. Simulation quotes carry `Ticker.QuoteType.SIMULATED`, depth sequences contain `SIMULATED`, and enabling the setting emits a warning. Simulation never marks broker market-data health available. The strategy signal agent's existing LIVE-quote gate also rejects simulated quotes.

Real-data availability:

- TWS: broker quotes and historical candles, with delayed/frozen quote status preserved.
- Client Portal: broker top-of-book ticker snapshots, preserving `_updated` and field 6509 availability. Incomplete/unsubscribed snapshots fail instead of creating prices. Historical candles are explicitly unavailable in this integration; use TWS for charts.
- Full depth: explicitly unavailable until an actual depth API is implemented. Top-of-book snapshots are not a substitute.
- Historical market prints: explicitly unsupported in this integration. Account executions are not market prints.
- In-progress candles: use a matching actual historical bar, or return an empty optional if that bar is absent. No invented OHLC from one ticker.

Resolve the broker contract before requesting real data. Errors propagate as failed futures, mark broker data unavailable, and retain subscription errors. REST calls use four daemon workers and a bounded queue of 64; saturation fails rather than blocking JavaFX. TWS async futures are composed directly. Synchronous CandleDataSupplier.getCandleData() remains a blocking compatibility API; UI consumers should use get(), which starts asynchronously.

Real-time Level 1/API market-data permissions depend on the contract and exchange. Level 2 depth requires its own entitlement as well as a future implementation. Delayed/frozen prices remain identifiable; subscriptions do not make an unimplemented API feature available.

The candle/trade models currently have no provenance field. Adding per-bar/per-print provenance across the shared data pipeline remains a follow-up; simulation is isolated here through explicit configuration, quote status, logs, and the LIVE guard.

Reference: https://www.interactivebrokers.com/docs/web-api/v1/endpoints/market-data/market-data-fields (field 6509 and top-of-book fields).

Client Portal contract candidates without a broker conId remain unresolved. The adapter no longer invents a conId by hashing a forex symbol, and the provider rejects cached contracts tagged syntheticCashContract. Resolve those instruments through a real broker contract lookup before requesting data.

## Files changed in this refactor

Production:
- src/main/java/org/investpro/exchange/ibkr/IbkrMarketDataProvider.java
- src/main/java/org/investpro/exchange/ibkr/IbkrExchange.java
- src/main/java/org/investpro/exchange/ibkr/IbkrClientPortalClient.java
- src/main/java/org/investpro/exchange/ibkr/IbkrTwsSession.java
- src/main/java/org/investpro/exchange/ibkr/IbkrMarketDataException.java
- src/main/java/org/investpro/models/trading/Ticker.java

Tests:
- src/test/java/org/investpro/exchange/ibkr/IbkrMarketDataProviderTest.java (17 new tests)
- src/test/java/org/investpro/exchange/ibkr/IbkrTwsSessionTest.java (quote-cache rejection regression)
- src/test/java/org/investpro/exchange/ibkr/IbkrIntegrationTest.java (explicit simulation fixture)

Documentation: IBKR_MARKET_DATA_SAFETY.md.

## Verification

The complete Maven clean test lifecycle compiled 1,212 main sources and 168 test sources. All 72 IBKR tests passed. The full suite ran 815 tests with one failure, three errors, and one skip. Failures were confined to AssistantTelegramSettingsTest (three mocked Preferences null errors) and TelegramRemoteDeskTest.uppercaseSettingsWorkWhenLowercaseSettingsAreBlank (expected null, received 123). Those unrelated implementations/tests were not changed.

Build output was redirected to output/ibkr-market-data-target through a temporary copy of the root POM, preserving project modules, compiler settings, plugins and packaging. This avoids the running application's locks on target. The original POM was not altered. See output/ibkr-market-data-clean-test.log and output/ibkr-market-data-package.log for complete results.

Final packaging (`package -DskipTests`) succeeded. The artifact is output/ibkr-market-data-target/investpro-1.0.0-SNAPSHOT.jar, with dependencies in its lib directory. The packaged IbkrMarketDataProvider, IbkrExchange, IbkrClientPortalClient, IbkrTwsSession, IbkrMarketDataException, Ticker, and Ticker$QuoteType classes were verified present and SHA-256 identical to the compiled classes. The temporary build POM was removed.

Subscription references:
- https://www.interactivebrokers.com/docs/general/market-data-subscriptions/introduction
- https://www.interactivebrokers.com/docs/tws-api/doc/market-data-live/market-depth-exchanges/introduction
