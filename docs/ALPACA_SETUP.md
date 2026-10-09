# Alpaca connection and supported features

Use the API key ID and secret from your **live Alpaca trading account** in InvestPro's Alpaca credential fields. Authentication contacts `https://api.alpaca.markets/v2/account`; merely entering credentials does not count as authentication. A successful login does not override account trading restrictions.

InvestPro's Alpaca adapter currently supports US equities: active stock discovery, account balances and positions, snapshots, historical/in-progress bars, polling updates, market/limit orders, whole-share trailing stops, open-order queries, and cancellation. Asset `tradable`/`fractionable` flags and account status/block flags are checked before order submission. Alpaca remains responsible for buying-power, short-sale, market-session and other order acceptance rules. An accepted order ID is not a confirmed fill.

Market data comes from `https://data.alpaca.markets`. `ALPACA_DATA_FEED` defaults to `iex`, which covers one exchange rather than consolidated US market activity. You may select `sip` or `delayed_sip` if your subscription permits it. Permission errors identify the need to review account/data access; the adapter does not fabricate data. Supported bar intervals: 1, 5, 15, 30 minutes; 1, 4 hours; 1 day.

Paper trading remains local. Remote `paper-api` endpoints are disabled. Paper-account credentials cannot authenticate against the live endpoint; use live credentials for Alpaca data/account access and select local paper execution for simulation.

Crypto/options trading, native WebSocket streaming, depth/order-book data, live fill/order history, bracket orders and stop/stop-limit order submission are not implemented by this adapter. These capabilities are not advertised as available. Ticker, candle, account, order, position and balance updates use InvestPro's existing polling service.

Tests use mocked HTTP responses; they never submit real broker orders or use saved credentials.
