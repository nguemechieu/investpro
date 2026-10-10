# Runtime execution and UI responsiveness

InvestPro uses application-owned `AppExecutors` to isolate market data, strategy,
trading, risk, backtest, I/O and assistant work. Fixed worker counts and bounded
queues use rejection rather than running overflow work on the caller's thread.
`AppExecutors.submit()` returns failures through its future and interrupts and
removes cancelled queued work. The shared scheduler dispatches polling work;
it does not perform exchange requests itself.

`AgentRuntime` gives each agent a bounded ordered lane over the appropriate
worker pool. This preserves an agent's event ordering without allocating a
thread per symbol. Runtime and execution generations discard queued work from
an earlier bot session. A trading stop does not stop application workers,
the application assistant or the core's read-only streams. It cannot recall
an order already sent to a broker.

`TradeExecutionCoordinator` prepares and validates orders on trading workers,
evaluates risk on risk workers, reviews AI decisions on assistant workers and
returns to trading workers for approved execution. Existing position locks,
portfolio locks, pending-order checks and final risk approval remain in place.
State-store values are for presentation; they do not replace execution-time
account, order or position validation.

Strategy Lab initializes its service and loads report snapshots in the
background. AI health queries, assignment persistence, Strategy Builder saves,
symbol resolution and Market Info request preparation also run outside JavaFX.
Inputs are captured before dispatch; scheduled strategy improvement does not
read JavaFX controls from a background thread.

`SystemStateStore` contains immutable exchange-scoped quotes, depth, accounts,
positions, orders, strategies and health. An older quote cannot overwrite a
newer quote. Empty position/order snapshots clear their stored state. Quotes
and books returned to the desktop are reconstructed from immutable values.

`UiUpdateBatcher` delivers presentation work on a 200 ms interval with at most
one JavaFX callback queued. Quote and account updates retain the latest value
per key. Discrete events use a separate bounded FIFO; execution events are
never routed through quote coalescing. Queue overflow is explicit rejection.
Closing a consumer cancels its timer and discards late presentation callbacks.

OANDA ticker subscriptions share a pricing poll and sequential batches of 20
instruments. An unfinished pricing request prevents the next poll from starting.
Only actual returned quotes are published; missing instruments get no invented
prices. OANDA does not start an exchange depth poll. Its existing account,
orders and positions polling remains throttled separately.
Asynchronous depth and private-data requests retain an in-flight guard until
completion. Results from a stopped stream generation are not delivered.

Streamed chart bars update cached data only when the symbol and timeframe
match. Updates preserve the visible window and zoom, with at most 1,000 live
bars retained. Historical loading and user-requested refresh remain separate.
Backtest progress is delivered every 100 bars and at completion.

Chart disposal and desktop teardown do not wait for worker termination on
JavaFX. Network teardown is registered as background cleanup. The application
finishes those cleanups before shutting down its shared workers.

`UiFreezeDetector` records a JavaFX heartbeat every 250 ms. A background watchdog
logs the UI stack when the delay exceeds 1.5 seconds, at most once per 30 seconds.
Use this with real gateway/exchange traffic to identify further bottlenecks;
unit tests do not establish a live-terminal latency guarantee.

## Coinbase startup and REST backpressure

Coinbase market REST requests, including chart and strategy candle history, use
`AppExecutors.COINBASE_REST`: two HTTP workers and a bounded queue of 128 requests.
This pool is separate from the market polling workers that wait for its results.
The existing limiter still enforces request spacing, product cooldowns, and its
circuit breaker. Retries release their permits before another attempt; interrupted
pacing also returns its permit. Request cancellation removes queued work.

Coinbase polling subscriptions start at different offsets within their polling
period. HTTP 429 responses defer the affected product until its cooldown expires
rather than sleeping through that cooldown on an HTTP worker. They are logged as
rate limits; temporary limiter deferrals do not count as trading execution failures
or send Telegram error alerts. Genuine stream failures continue to send one
Telegram alert with the exchange, error type, and safe error details, plus email
when configured. The running bot's event listener owns Telegram error delivery.
