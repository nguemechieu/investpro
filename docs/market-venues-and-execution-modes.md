# Product venues and execution modes

A route identifies an exchange or product destination. Paper trading describes
how an order executes and is not a route, market, asset class or exchange.

Onboarding uses **Product venue**, with named destinations grouped by the selected
exchange. Examples include Coinbase Advanced, Coinbase Derivatives, Coinbase
International, IBKR SMART, IBKR CME and IBKR IDEALPRO. Contract and underlying
asset selections determine the default venue. Market watch shows the instrument's
normalized product venue.

Saved geographic labels such as US or International remain compatible. A saved
Paper Trading venue resolves to the real product destination using the exchange
and contract selection; this does not change its separately saved trading mode.

`ExecutionMode` contains LOCAL_PAPER, LIVE and BACKTEST. Strategy plans carry this
mode separately from their exchange venue. New plans default to LOCAL_PAPER.
Strategy lifecycle approval determines live eligibility; authentication, risk,
licensing and the bot's own execution settings still govern submission.

The strategy router returns actual exchange destinations, using canonical broker
names. Older Coinbase Advanced, Binance Spot and OANDA REST enum aliases resolve
to their canonical broker identities. If no healthy destination is available, the
plan has UNKNOWN venue and is invalid; the router does not silently change LIVE
to paper mode. Local plans cannot submit blockchain transactions.

The smart router likewise preserves the real exchange and venue category in
`ExecutionRoute`, alongside a separate executionMode. Paper requests respect the
same exchange/venue restrictions as live requests. Existing paperMode request
fields and the previous route constructor remain available for source compatibility.

Paper/simulation constants were removed from both execution-venue enums. External
callers using those constants must select the actual venue and set execution mode
separately. Legacy paper venue strings must not be interpreted as permission for
live execution.
