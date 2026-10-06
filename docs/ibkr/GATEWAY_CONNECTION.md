# IB Gateway / TWS connection

InvestPro uses one official IBKR socket API session for managed accounts, contract discovery,
market data and supported order submissions. An open TCP port is not proof of API readiness:
the session must receive both `nextValidId` and `managedAccounts`.

Install the external official Java API once from the repository directory:

```powershell
.\setup-ibkr-api.ps1
```

This downloads API 10.50.02 and its protobuf runtime into `lib/ibkr`. The SDK is loaded
separately from InvestPro's protobuf dependency. Its license is copied alongside the jars;
the original source archive is retained under `output/ibkr-sdk-install`.
Restart InvestPro after installation and rebuilding. If launching elsewhere, use
`-Dinvestpro.ibkr.apiDirectory=C:/path/to/investpro/lib/ibkr`.

Log in using IB Gateway or TWS, enable socket API clients, and configure InvestPro's
host, socket port and a unique client ID. No IBKR username/password/API key is required
in InvestPro. Defaults are Gateway live `4001` and paper `4002`; TWS commonly uses
live `7496` and paper `7497`. The configured endpoint survives reconnects.
Bot simulation mode does not choose the Gateway's paper/live socket port.

`IBKR_WATCHLIST` is a comma-separated stock watchlist. Its default is
`AAPL,MSFT,NVDA,AMZN,GOOGL,META,TSLA,SPY`; each symbol is resolved through IBKR contract
details before it appears in the market watch. This is a watchlist, not the full IBKR universe.

For Docker, install the SDK on the host; Compose mounts `./lib/ibkr:/app/lib/ibkr:ro`.
Use a Gateway host reachable from
the container, such as `host.docker.internal`, rather than container localhost.

Market, limit and stop orders use broker order IDs and broker acknowledgements.
Unsupported native bracket, stop-limit and trailing-stop submission fails explicitly;
it must not be reported as an executed local order. API read-only settings and IBKR
account permissions can still reject an order even when the connection is ready.
