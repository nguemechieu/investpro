# InvestPro current status

Updated: 2026-10-05. Development baseline: JDK 27, JavaFX 27, Maven 3.8.5+.

InvestPro is actively developed. Implemented adapters and passing unit tests do not
establish that every broker, asset type or deployment is operational.

| Area | Current behavior / limitation |
|---|---|
| Trading desk | Exchange/mode selection, credential dialogs and navigation |
| Charts | Exchange-supported timeframe selector, detach/reattach, identity-based node tracking |
| Market Watch | Product/availability filters, sorting, session-local favorites, CSV export |
| Authentication | Private endpoints require authentication; account connection is separate from market streams |
| PAPER | Local session simulator; market fills, balances, pending orders and cancellation |
| LIVE | Authenticated broker routing; venue capabilities and restrictions still apply |
| Strategy Lab | Backtest queue, worker scheduling and strategy assignment |
| Local Python AI | Eight unary advisory RPCs; rule-based models; streaming RPCs not implemented |
| Telegram/OpenAI | Authorized private-chat commands, previews, confirmations and separate AI conversation history |
| Docker | Desktop/noVNC plus PostgreSQL configuration; runtime verification remains required |
| Static analysis | Configured SpotBugs is skipped on unsupported JDKs including 27 |

Latest targeted verification: 13 Telegram/paper-execution tests passed on JDK 27.
Earlier chart/timeframe checks passed six tests. A full clean final-revision suite,
live integration checks and deployment validation remain release requirements.

Paper execution starts with USD 10,000 and is not persisted across app restarts.
Pending limit/stop-style paper orders do not have a matching engine. Telegram
watchlists and conversations are session-local; `/news` has no live news source.

See [release readiness](PRODUCTION_READY.md), [release checklist](PRODUCTION_RELEASE_CHECKLIST.md),
[remote desk](docs/telegram-remote-desk.md), [local AI](ai-service/README.md), and
[documentation index](docs/README.md). Session summaries describe their original
revision and are not current status reports.
