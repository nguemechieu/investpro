# Local paper trading

Paper trading executes inside InvestPro. It never submits or cancels orders on a
broker's paper, practice, demo, sandbox or development network. Market data may
still come from the production exchange; fetching quotes is separate from order
execution.

The shared `LocalPaperExecution` provider keeps simulated orders and balances in
memory for the current exchange session. Existing adapter-specific local
simulators remain local. Paper mode does not authenticate a live trading session.
Bot PAPER mode uses local execution even when the user has a connected live
exchange for market data.

Legacy PAPER, SANDBOX, PRACTICE, DEMO and TESTNET trading-mode values resolve to
local PAPER mode. Existing sandbox credential flags remain compatibility inputs
for local mode; they do not select remote test endpoints.

## Broker settings

- Alpaca uses the production endpoint for broker access. A configured
  `ALPACA_BASE_URL` containing `paper-api` is rejected. Remove that override for
  production data and use local PAPER execution to simulate orders.
- OANDA uses production REST and streaming endpoints. Failed authentication no
  longer retries against practice accounts or changes trading mode automatically.
  Paper orders, account balances, order history and cancellation use local state.
  Broker-only account and portfolio mutations are blocked in PAPER mode.
- Schwab direct order calls, cancellation, balances and order history use local
  state in PAPER mode. Paper mode cannot reach the broker submission API.
- IBKR onboarding offers **Local paper simulation** and **Live account**. Local
  paper needs no gateway handshake. Remote paper profiles and standard paper
  socket ports are rejected; paper (`DU`) accounts cannot submit or cancel orders
  through the native API. Use a live gateway only for actual broker connectivity.
  Saved paper profiles are not automatically upgraded to live trading permission.
- Stellar paper simulation uses production/mainnet market data and asset issuers.
  Solana legacy devnet/testnet settings use mainnet data with live execution
  disabled; migration does not grant live trading permission.

Authenticated live execution continues to require the application's existing
authentication, risk and licensing checks. No real orders are required to test
paper routing.
