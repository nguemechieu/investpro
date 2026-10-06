# Production Release Checklist

> Current documentation baseline (2026-10-05): JDK 27, JavaFX 27 and Maven 3.8.5+. Use the [documentation index](docs/README.md) for current setup, trading-desk behavior, Telegram commands and release limits. Design examples below are not proof of broker support or deployment verification.

Use this checklist before any production release.

Last updated: 2026-10-05

## 1. Build and Quality Gates

- [ ] Run full production verification:

```powershell
.\mvnw.cmd verify -Pproduction
```

- [ ] Confirm no failing tests.
- [ ] Confirm enforcer checks pass (Java/Maven/dependency convergence).
- [ ] Record that the configured SpotBugs check is skipped on JDK 27; require a compatible parser before claiming static-analysis coverage.
- [ ] Run compile smoke check from a clean terminal:

```powershell
.\mvnw.cmd -DskipTests compile
```

- [ ] If running targeted tests, quote the `-Dtest` argument on PowerShell:

```powershell
.\mvnw.cmd "-Dtest=org.investpro.exchange.ibkr.IbkrIntegrationTest,org.investpro.spi.PluginRegistryTest" test
```

- [ ] Ensure no running process is locking files under `target\` before `clean`.

## 2. Configuration and Secrets

- [ ] Copy `.env.example` to `.env` and set production values.
- [ ] Set `APP_ENV=production`.
- [ ] Set `APP_DEBUG=false`.
- [ ] Set `APP_STRICT_STARTUP_VALIDATION=true`.
- [ ] Set `APP_HEARTBEAT_STALE_SECONDS` to your SLO target.
- [ ] Set `DEFAULT_ACCOUNT_MODE` explicitly (`PAPER` or `LIVE`).
- [ ] Verify every enabled exchange has credentials:
- [ ] If `ENABLE_COINBASE=true`, set `COINBASE_KEY_NAME` and `COINBASE_PRIVATE_KEY`.
- [ ] If `ENABLE_BINANCE=true`, set `BINANCE_API_KEY` and `BINANCE_API_SECRET`.
- [ ] If `ENABLE_OANDA=true`, set `OANDA_API_KEY` and `OANDA_ACCOUNT_ID`.
- [ ] If `ENABLE_TELEGRAM=true`, set `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_IDS` and `TELEGRAM_CHAT_ID`.
- [ ] If `ENABLE_OPENAI=true`, set `OPENAI_API_KEY`.

## 3. Runtime and Monitoring

- [ ] Open the System & Operations board and verify:
- [ ] heartbeat updates regularly,
- [ ] runtime error counters stay stable,
- [ ] memory usage remains under expected threshold.
- [ ] Confirm logs include startup profile and health checks.

## 4. Strategy Startup Readiness

- [ ] Confirm startup does not block on strategy backtesting.
- [ ] Verify each selected symbol gets an immediate fallback strategy assignment.
- [ ] Verify background selection upgrades assignments when backtests complete.
- [ ] Verify trading starts immediately while reassignment runs in background.

## 5. Go-Live Safety

- [ ] Perform smoke test in `PAPER` mode first.
- [ ] Validate exchange connectivity and market data subscriptions.
- [ ] Validate risk controls and order rejection paths.
- [ ] Switch to `LIVE` only after smoke test passes.

## 6. Broker Integration Release Checks

- [ ] Validate IBKR paper connectivity (host, paper port, account sync).
- [ ] Validate IBKR live-trading safety gates remain enforced.
- [ ] Verify IBKR workspace panels load (connection, account, portfolio, positions, orders).

## Current integration checks

- [ ] Verify paper orders remain local, including when a broker is authenticated.
- [ ] Test timeframe changes and both chart reattach buttons plus stage close.
- [ ] Test Telegram allowlists, confirmation expiry, user isolation and mode changes.
- [ ] Verify the Python AI bound port matches the Java client port.
- [ ] Verify Docker/noVNC and the AI image entrypoint workaround on the target host.

See [current readiness](PRODUCTION_READY.md) and [documentation index](docs/README.md).
