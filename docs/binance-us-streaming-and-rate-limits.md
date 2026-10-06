# Binance.US shared state and request budget

## Cause and call-site audit

`TradeExecutionCoordinator.hasPendingOrder()` called `fetchOpenOrders(symbol)` for each guarded signal. Previously that queued a signed REST request on the common pool, even though the exchange already represented a shared account. `TradingDesk.fetchOpenOrdersForWorkspace()` and the terminal adapter also requested order snapshots; the workspace could issue a second request when the first was empty. The generic polling fallback requested account information every 15 seconds and open orders every 20 seconds. Binance.US advertised private streaming as unsupported and its account/order/fill stream methods were placeholders.

The old signed/public spacing did not account for endpoint weights, response weight headers, concurrent metadata discovery, or candle requests using a separate HTTP client. Failed order polls returned empty lists, and account throttling could return synthetic paper balances for a live account. Those fallbacks concealed missing state.

The fallback timers alone represented four account requests and three account-wide open-order requests per minute, per poller. With the new conservative reservations, that corresponds to 80 + 120 weight/minute. Each signal guard previously added a symbol open-order request (weight 3). Each uncached tradability/discovery lookup fetched `exchangeInfo` (weight 20). Candle pagination added independent requests. Aggregate historical usage cannot be calculated reliably from the supplied error: it contains neither request counts nor agent signal rates, and weight is shared with other clients using the same IP.

Other paths audited:

| Path | Caller and resulting behavior |
| --- | --- |
| `/api/v3/openOrders` | Agent, workspace and terminal reads are local. One account-wide request per synchronization. Symbol-specific DELETE remains an explicit cancellation. |
| `/api/v3/account`, balances | Startup/recovery/integrity synchronization; account and balance reads use one shared live snapshot. |
| `/api/v3/order` | Order reads use the cache; `synchronizeOrderFromRest(pair, id)` is explicit. POST/DELETE remain network operations and update cached state. |
| `/api/v3/allOrders` | Explicit historical queries remain; workspace history refresh is already limited to 300 seconds. |
| `/api/v3/myTrades` | Controlled recovery of fills for previously tracked symbols after a private-stream gap; paginated and deduplicated. Ordinary fill reads are local. |
| `/api/v3/exchangeInfo` | Shared, coalesced 10-minute public snapshot. Failed metadata refresh retains the previous snapshot with a stale diagnostic. |
| Tickers/depth | WebSocket snapshots populate exchange-level caches. Identical public REST fallbacks are coalesced with short TTLs. |
| `/api/v3/klines` | Historical requests remain; every Binance.US candle supplier and its clones use the exchange's transport, executor and weight budget. |
| `/api/v3/trades` | Explicit recent/historical fallback remains and shares the public cache and budget. |
| `/api/v3/time` | Shared transport; server-clock synchronization retains its five-minute TTL. |

`BinanceUsActivityService` currently reports that REST activity synchronization is not wired; it does not generate the excessive account requests. No separate position REST polling was introduced for spot balances.

## Account synchronization

One `BinanceUsAccountState` belongs to each exchange instance and is shared by all its consumers. Its synchronized operations guard orders, balances, archived order state, fills and reconciliation buffers. Public order/account/fill reads return independent objects rather than exposing mutable cached values.

The authenticated socket uses `wss://ws-api.binance.us:443/ws-api/v3` and `userDataStream.subscribe.signature`, with sorted HMAC parameters and numeric timestamps. This follows the [current Binance.US API documentation](https://docs.binance.us/#subscribe-to-user-data-stream-websocket); no new listenKey subscription or keepalive mechanism is used.

Startup establishes the subscription and performs one authoritative account-wide open-order REST snapshot plus one account snapshot. Incoming events are buffered during synchronization and replayed over REST results. A subscription timeout permits a stale REST snapshot; later stream restoration reconciles it. Routine agent reads never initiate synchronization.

`NEW` maps to the domain's `OPEN`, and Binance's `CANCELED` maps to `CANCELLED`. Partial fills update the order; terminal events remove it from open orders while retaining its record. Duplicate fills are keyed by symbol, order id and trade id. Older snapshots cannot reopen known terminal orders. Orders absent from a snapshot without a terminal reason are retained as `UNKNOWN`, rather than inventing a fill/cancel reason. REST acknowledgements received during reconciliation are buffered too.

Absolute balance events are versioned by asset. Deposit/withdrawal and external-lock deltas use decimal arithmetic and duplicate-event keys. Stream callbacks publish cached state without issuing REST calls.

Reconnection uses a single scheduled retry with bounded exponential delay and jitter. Restoration triggers one coalesced synchronization and recovers fills for symbols already tracked by the account state, with an overlapping time range to deduplicate against previously streamed executions. This is a session recovery cache, not a complete historical ledger of every externally traded symbol; explicit historical queries remain necessary for that scope.

The shared cache reports `LIVE`, `STALE`, `SYNCING` or `DISCONNECTED`, plus the last successful reconciliation and last user-data event. Failed or rejected synchronization retains the previous valid state and logs a warning. It never substitutes paper balances for a live account. A single 15-minute integrity task performs low-frequency reconciliation. Manual synchronization is available through `synchronizeAccountState()`.

## Request limiting

Each exchange has one `BinanceUsRequestBudget`, four bounded REST workers, a 128-task queue and four in-flight transport permits. Requests are reserved against a conservative 4,800-weight minute budget, leaving a 20% margin below 6,000. Endpoint-specific reservations include symbol/account-wide open orders, account information, discovery, history, trades, ticker variants, candles and depth tiers. Unknown endpoints reserve conservatively. WebSocket API connection and subscription weights also share this IP-weight budget.

The limiter incorporates `X-MBX-USED-WEIGHT-1M`, `X-MBX-USED-WEIGHT` and WebSocket rate-limit observations. Reservations remain spaced across minute rollover and cooldown recovery. Signed requests reserve capacity before creating their timestamp/signature.

HTTP 429/418 produces `BinanceUsRequestBudget.RateLimitedException`, an `IOException` carrying the retry deadline. Numeric/date `Retry-After`, ban deadlines and bounded jitter determine global cooldown. Queued/new requests check it before transport; expected throttling is never retried as a generic connection failure. Previously valid account state remains available with stale health.

`getBinanceDiagnostics()` exposes weight usage, estimated remaining capacity, cooldown, cache size, last reconciliation/event, account health and stale metadata count. REST and reconciliation logs use debug level except degradation warnings; market ticks do not produce new metrics logs.

## Files and verification

Implementation files: `BinanceUs.java`, `BinanceUsAccountState.java`, `BinanceUsRequestBudget.java`, `BinanceUsRestCache.java`, `BinanceCandleDataSupplier.java` and `PollingExchangeStreamer.java`. The generic poller delegates Binance.US private subscriptions to its native stream; other exchanges keep their existing behavior. Tests: `BinanceUsAccountStateTest`, `BinanceUsCooldownTest` and `BinanceUsSharedStateTest`, plus the existing `PollingExchangeStreamerTest` regression suite.

Build output is isolated under `output/binance-architecture-target` because the running JavaFX application holds files in the normal `target` directory. A temporary Maven descriptor changes only the build directory; the repository's `pom.xml` is unchanged by this task.

The full clean test run executed 464 tests with five failures, four errors and one skip. Its failures match the earlier build: four Coinbase market-type assertions, one strategy compatibility assertion, two Coinbase discovery reflection errors and two Coinbase escaped/quoted private-key parsing errors. See `output/binance-architecture-clean-test.log`.

The final focused `clean package` succeeded: 32 tests passed with no failures, errors or skips. See `output/binance-architecture-package.log`. These tests exercise shared startup, cache-only agent reads, lifecycle transitions, duplicate executions/balances, reconciliation races, fill recovery, retained stale snapshots, executor rejection, metadata degradation, global cooldown, weight headers and paced recovery.

Artifact inspection confirmed that `output/binance-architecture-target/investpro-1.0.0-SNAPSHOT.jar` contains `BinanceUs`, `BinanceUsAccountState`, `BinanceUsRequestBudget`, its specific rate-limit exception, `BinanceUsRestCache`, and the existing `ChartColors` class. The temporary Maven descriptor was removed after verification. The running application must be rebuilt/restarted to load the changed implementation; its process was left running.

Remaining significant REST consumers are bulk history and recovery queries, large depth snapshots, recent trades and cold historical candle pagination. They share the budget; other applications on the same IP can still force Binance.US throttling. Authenticated production connectivity was not exercised with live credentials, and no trade was submitted for validation.
