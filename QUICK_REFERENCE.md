# InvestPro quick reference

Updated: 2026-10-05. JDK 27 / JavaFX 27 / Maven 3.8.5+.

```bash
# Linux/macOS, from the repository root
./mvnw clean test
./mvnw clean package
./mvnw javafx:run
```

On Windows use `.\mvnw.cmd` instead. `run-app.bat` starts the packaged desktop
with copied dependencies. Set JAVA_HOME to the JDK 27 installation.

- [Trading Desk](docs/trading-desk.md): mode/exchange credentials, timeframe selection, detach/reattach and Market Watch.
- [Telegram](docs/telegram-remote-desk.md): allowlisted private-chat commands, confirmations and OpenAI questions.
- [Python AI](ai-service/README.md): separate process, generated gRPC stubs, bind/client keys and Docker workaround.
- [Docker](DOCKER_SETUP.md): PostgreSQL and desktop/noVNC services, storage and access.
- [Release checks](PRODUCTION_RELEASE_CHECKLIST.md): clean tests, interactive flows and deployment verification.
- [All documentation](docs/README.md): current guides, design references and historical reports.

PAPER stays local even when an exchange is authenticated. Its balances and orders
are session-local, and pending limit/stop-style orders have no matching engine.
LIVE needs the selected broker's authentication and supported capabilities.
Configured SpotBugs is skipped on JDK 27; passing verify does not establish that
static analysis ran. Current release status is active development.
