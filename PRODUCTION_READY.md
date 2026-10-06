# InvestPro release readiness

Updated: 2026-10-05. Version: 1.0.0-SNAPSHOT. Status: active development.

This document is a release assessment guide, not a production certification.
The earlier blanket claims of zero warnings, measured coverage and completed
end-to-end deployment verification are not supported by the current evidence.

## Build baseline

- JDK 27 and JavaFX 27; Maven 3.8.5 or newer.
- The Maven wrapper pins Maven 3.8.5.
- Python 3.11 is the optional local AI container baseline.
- Desktop builds include platform-specific JavaFX runtime artifacts.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify -Pproduction
```

Use `./mvnw` on Linux/macOS. The production profile enforces Java/Maven versions
and dependency convergence. Its configured SpotBugs parser cannot handle Java 27
class files, so the unsupported-JDK profile skips that check. A successful verify
is not evidence that static analysis ran. Compiler/runtime warnings remain.

## Evidence and outstanding verification

The latest targeted Telegram/paper-execution verification passed 13 tests on JDK 27.
Earlier chart/timeframe verification passed six targeted checks. These observations
do not replace a full clean suite on the final revision or interactive testing.

Before release, verify:

- Desktop navigation, narrow windows, credential dialogs and chart timeframes.
- Detach/reattach through both buttons and closing the detached window.
- Authentication state independently of WebSocket connection state.
- PAPER orders remain local; LIVE orders use the authenticated chosen broker.
- Broker-specific quantity, precision, product and account restrictions.
- Telegram allowlists, command menu, confirmation expiry and uncertain responses.
- Local AI startup, actual bound port, health and fallback behavior.
- Docker/noVNC, persistent data, restart behavior and credential provisioning.

Local paper execution starts with a session-local USD 10,000 balance. Market orders
fill locally using a supplied quote; pending limit/stop-style orders lack a matching
engine. Watchlists and AI conversations are in memory. Telegram/OpenAI live
end-to-end behavior and Docker deployment were not verified by the latest tests.

## Deployment notes

The root Compose file includes PostgreSQL and the desktop/noVNC container; it does
not include the Python AI service. Compose contains development database/VNC
passwords and publishes host ports. Review that configuration before deployment.
Port 8080 is published but publication alone does not establish an HTTP API.

Installer creation is available through the Maven installer profile but is not a
verified release path. The current jpackage arguments pass the SNAPSHOT project
version, which may require a platform-compatible installer version override.

Use the [release checklist](PRODUCTION_RELEASE_CHECKLIST.md),
[Docker guide](DOCKER_SETUP.md), [security guidance](SECURITY.md), and
[documentation index](docs/README.md) for the release review.
