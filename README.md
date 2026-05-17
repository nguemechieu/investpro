<![CDATA[<p align="center">
  <img src="src/main/resources/images/Invest.png" alt="InvestPro Logo" width="120" />
</p>

<h1 align="center">InvestPro</h1>

<p align="center">
  <strong>A serious, multi-asset trading workstation built on Java 21 + JavaFX.</strong><br/>
  Strategy engine · Agent runtime · AI-assisted analysis · Risk management · Backtesting · Paper trading
</p>

<p align="center">
  <a href="https://github.com/nguemechieu/investpro/actions/workflows/maven.yml">
    <img src="https://github.com/nguemechieu/investpro/actions/workflows/maven.yml/badge.svg" alt="Java CI"/>
  </a>
  <a href="https://github.com/nguemechieu/investpro/actions/workflows/docker-image.yml">
    <img src="https://github.com/nguemechieu/investpro/actions/workflows/docker-image.yml/badge.svg" alt="Docker Build"/>
  </a>
  <a href="https://github.com/nguemechieu/investpro/actions/workflows/codecov.yaml">
    <img src="https://github.com/nguemechieu/investpro/actions/workflows/codecov.yaml/badge.svg" alt="Code Coverage"/>
  </a>
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"/>
  </a>
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21"/>
  <img src="https://img.shields.io/badge/JavaFX-21.0.6-blue.svg" alt="JavaFX"/>
  <img src="https://img.shields.io/badge/status-active%20development-yellow.svg" alt="Status"/>
</p>

---

## Overview

**InvestPro** is an open-source, desktop trading workstation for serious traders and developers. It provides a unified interface for multiple exchanges and brokers, a pluggable strategy engine, an AI-assisted signal pipeline, and a robust risk management layer — all wrapped in a native JavaFX UI that runs on Windows, Linux, and macOS.

It is designed to be a complete trading research and execution platform: from initial idea (strategy lab, backtesting) through validation (paper trading) to live execution — with institutional-quality controls at every stage.

> **InvestPro does not guarantee profits. Trading carries significant financial risk. Always validate your strategy with paper trading before committing real capital. See the [Disclaimer](#disclaimer).**

---

## Project Status

> 🚧 **Active Development** — Core features are working. The codebase is evolving. Some modules are still being stabilised. Paper trading is fully supported; live trading is available but should be used with care.

| Area | Status |
|---|---|
| Exchange adapters (8 venues) | ✅ Implemented |
| Real-time WebSocket streaming | ✅ Working |
| Strategy engine + signal pipeline | ✅ Working |
| Agent runtime (SmartBot) | ✅ Working |
| Paper trading | ✅ Working |
| Backtesting (StrategyLab) | 🔄 In progress |
| AI reasoning (OpenAI integration) | ✅ Working |
| Risk management layer | ✅ Working |
| Docker / VNC deployment | ✅ Working |
| Mobile companion / web UI | 📋 Planned |

---

## Screenshots

| Trading Desk | Market Watch + Signals |
|---|---|
| ![Trading Desk](src/main/resources/images/InvestPro-USD-JPY-20260509-161851.png) | ![Trading Desk 2](src/main/resources/images/Screenshot%202026-05-09%20162703.png) |

> *Screenshots reflect a development build. UI continues to evolve.*

**Docker VNC Access**

Access the full desktop via browser at `http://localhost:6080/vnc.html?autoconnect=1&resize=scale`

![VNC](src/main/resources/images/investpro_docker_vnc_screen.png)

---

## Why InvestPro?

Most open-source trading tools are either raw algorithmic frameworks (no UI) or simple bots (single exchange, single strategy). InvestPro bridges the gap:

- **Full desktop workstation** — live charts, market watch, order panel, portfolio view in one window
- **Multi-exchange, unified API** — switch exchanges without rewriting your strategy
- **Strategy-first architecture** — strategies and agents are first-class citizens, not afterthoughts
- **Built-in safety layers** — signal filter → risk review → AI reasoning → execution coordinator before any order reaches the market
- **Paper trading from day one** — validate everything before going live
- **Extensible** — add your own exchange adapter, strategy, or agent with clean interfaces

---

## Core Features

### Market Data & Streaming
- Real-time WebSocket feeds: ticker, trades, order book depth, OHLCV candles
- Multiple timeframes: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1M
- Intelligent HTTP rate-limit handling (auto-cooldown on 429/418 errors)
- REST fallback when WebSocket is unavailable

### Trading Capabilities
- Order types: market, limit, stop-loss, take-profit, bracket
- Paper trading with configurable virtual balance
- Live trading via authenticated exchange APIs
- Order lifecycle management (create, cancel, track fills)

### Strategy Engine
- Pluggable `TradingStrategy` interface
- `StrategyEngine` runs multiple strategies concurrently per symbol
- `StrategyLab` for backtesting and forward-testing
- `StrategyCatalog` of built-in strategies (trend-following, mean-reversion, momentum)
- `StrategyBootstrapper` for automatic strategy initialisation at startup
- `SignalToDecisionFilter` — all signals are reviewed before execution

### Agent Runtime (SmartBot)
- `AgentRuntime` manages the full lifecycle of all trading agents
- `AgentEventBus` — publish/subscribe event system decoupling all components
- `SymbolAgent` — per-symbol evaluation agent (analysis → signal → execution pipeline)
- `SymbolAgentManager` — manages all active symbol agents
- Auto-trading starts **OFF** by default; must be explicitly enabled

### Risk Management
- `RiskManagementSystem` — evaluates every signal before execution
- Per-trade risk limits (configurable `maxRiskPerTrade`, `maxDailyLoss`)
- `TradeExecutionCoordinator` — final execution gate combining risk + AI reasoning
- Small-account mode with reduced position sizing
- Symbol cooldown between open/close cycles to prevent thrashing

### AI-Assisted Analysis
- `AiReasoningService` interface — pluggable AI backend
- `OpenAiReasoningService` — GPT integration for trade reasoning (optional)
- `LocalAiReasoningService` — offline fallback
- AI can approve or reject signals; **AI never executes directly**
- Telegram ChatGPT integration when OpenAI key is configured

### Charting & Technical Analysis
- Interactive multi-timeframe candlestick charts
- Technical indicators: MA, EMA, RSI, MACD, Bollinger Bands, ATR, and more
- Zoom/pan, drawing tools, support/resistance annotation

### Notifications
- Telegram bot: real-time alerts, Telegram command handler (`/setapikey`, `/status`, etc.)
- Email notifications (SMTP, configurable)
- `SignalMonitorService` — logs every signal stage through the pipeline

### System Monitoring
- `SystemMonitorService` — 9-subsystem health dashboard (Exchange, Market Data, Account, Strategy, Risk, Execution, Agents, AI, Notifications)
- Real-time network latency to exchange endpoints
- Event log (SQLite-persisted via `EventLogRepositoryImpl`)

### Deployment
- Docker image with JavaFX + Xvfb + Fluxbox + x11vnc + noVNC
- Web VNC access (browser-based, no client install required)
- Native VNC for lower latency
- PostgreSQL support for production deployments

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        JavaFX UI Layer                          │
│   TradingDesk · MarketWatchPanel · ChartPanel · OrderPanel      │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                         SystemCore                              │
│  Application composition root — wires all components           │
│  EventBusManager · SignalMonitorService · SystemMonitorService  │
└──────┬──────────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │          │
  ┌────▼────┐ ┌──▼───┐ ┌────▼────┐ ┌──▼────┐ ┌──▼──────────┐
  │SmartBot │ │Strat- │ │  Risk   │ │  AI   │ │  Exchange   │
  │AgentRun-│ │egyEng-│ │Managmt  │ │Reason-│ │  Adapters   │
  │time     │ │ine    │ │System   │ │ing    │ │             │
  └────┬────┘ └──┬───┘ └────┬────┘ └──┬────┘ └──────┬──────┘
       │         │          │         │              │
  ┌────▼─────────▼──────────▼─────────▼──────────────▼──────┐
  │                  TradeExecutionCoordinator                │
  │         Signal → Risk review → AI reasoning → Execute     │
  └───────────────────────────────────────────────────────────┘
```

**Signal pipeline:**
```
StrategySignal
  └─→ SignalToDecisionFilter
        └─→ BotTradeDecisionEngine
              └─→ RiskManagementSystem  (approve / reject)
                    └─→ AiReasoningService  (approve / reject)
                          └─→ TradeExecutionCoordinator
                                └─→ ExecutionEngine  →  Exchange API
```

**Package layout:**
```
src/main/java/org/investpro/
├── core/                   # SystemCore, SmartBot, agents, runtime
│   ├── agents/             # AgentRuntime, AgentEventBus, SymbolAgent
│   ├── bot/                # SmartBot
│   └── agents/execution/   # ExecutionEngine, TradeExecutionCoordinator
├── exchange/               # Exchange adapters (Binance, Coinbase, Oanda, …)
├── strategy/               # StrategyEngine, StrategyCatalog, StrategyLab
├── decision/               # BotTradeDecisionEngine, SignalToDecisionFilter
├── risk/                   # RiskManagementSystem
├── ai/                     # AiReasoningService, OpenAiReasoningService
├── monitoring/             # SystemMonitorService, SignalMonitorService
├── event/                  # EventBusManager, EventPersistenceListener
├── ui/                     # JavaFX panels and windows
├── models/                 # TradePair, Order, Account, Ticker, …
├── repository/             # SQLite-backed repositories
└── data/                   # CandleData, Db1
```

---

## Supported Exchanges & Brokers

| Venue | Asset Classes | WebSocket | Paper | Live |
|---|---|---|---|---|
| **Binance** (Global) | Crypto spot | ✅ | ✅ | ✅ |
| **Binance US** | Crypto spot | ✅ | ✅ | ✅ |
| **Coinbase** | Crypto spot | ✅ | ✅ | ✅ |
| **OANDA** | Forex, CFD | ✅ | ✅ | ✅ |
| **Interactive Brokers** | Stocks, Futures, Forex, Options | ✅ | ✅ | ✅ |
| **Alpaca** | US Stocks, Crypto | ✅ | ✅ | ✅ |
| **Bitfinex** | Crypto | ✅ | ✅ | ✅ |
| **Stellar Network** | XLM / USDC (DEX) | ✅ | ✅ | ✅ |

> Live trading requires valid API credentials. Always test with paper trading first.

---

## Installation

### Prerequisites

| Requirement | Version |
|---|---|
| Java (JDK) | 21 LTS (Eclipse Temurin recommended) |
| Maven | 3.6+ |
| Git | Any recent version |
| Docker (optional) | 20+ |

```bash
java -version   # Must show 21+
mvn -version    # Must show 3.6+
```

Download Java 21: [https://adoptium.net/](https://adoptium.net/)

---

### Option 1 — Build from Source

```bash
# Clone
git clone https://github.com/nguemechieu/investpro.git
cd investpro

# Build (skip tests for speed)
mvn clean package -DskipTests

# Run
java -jar target/investpro-1.0.0-SNAPSHOT.jar
```

To include tests:
```bash
mvn clean package
```

---

### Option 2 — Docker (recommended for headless / server deployments)

```bash
git clone https://github.com/nguemechieu/investpro.git
cd investpro

# Start PostgreSQL + InvestPro with VNC
docker-compose up -d

# Open in browser
open http://localhost:6080/vnc.html?autoconnect=1&resize=scale
# Or connect with any VNC client to localhost:5900
# Default VNC password: investpro
```

**Ports exposed:**

| Port | Service |
|---|---|
| `6080` | noVNC web client |
| `5900` | Native VNC |
| `8080` | Application HTTP (future REST API) |
| `5432` | PostgreSQL |

**Docker image details:**
- Base: Eclipse Temurin 21 JDK (Ubuntu)
- Desktop: Xvfb + Fluxbox + x11vnc + noVNC
- Full clipboard support via `autocutsel`

See [`DOCKER_USAGE_GUIDE.md`](DOCKER_USAGE_GUIDE.md) for advanced configuration (resource limits, custom VNC password, troubleshooting).

---

### System Requirements

| | Minimum | Recommended |
|---|---|---|
| OS | Windows 10 / Ubuntu 20+ / macOS 10.15+ | Windows 11 / Ubuntu 22+ / macOS 12+ |
| RAM | 2 GB | 4 GB+ |
| Disk | 500 MB | 1 GB+ |
| CPU | Dual-core | Quad-core+ |
| Network | Stable broadband | Low-latency, wired |

---

## Configuration

### API Credentials

Create `~/.investpro/config.properties`:

```properties
# --- Binance US ---
binance.us.key=YOUR_API_KEY
binance.us.secret=YOUR_API_SECRET

# --- Binance Global ---
binance.key=YOUR_API_KEY
binance.secret=YOUR_API_SECRET

# --- Coinbase ---
coinbase.key=YOUR_API_KEY
coinbase.secret=YOUR_API_SECRET

# --- OANDA ---
oanda.token=YOUR_BEARER_TOKEN
oanda.account_id=YOUR_ACCOUNT_ID

# --- Alpaca ---
alpaca.key=YOUR_API_KEY
alpaca.secret=YOUR_API_SECRET

# --- Telegram Bot (optional) ---
telegram_token=YOUR_BOT_TOKEN

# --- Email notifications (optional) ---
from_email=you@example.com
to_email=alerts@example.com

# --- OpenAI (optional, for AI-assisted analysis) ---
openai.api_key=YOUR_OPENAI_KEY

# --- Risk limits ---
risk.small_account.enabled=true
risk.small_account.threshold=100.0
risk.small_account.oanda_units=1.0
```

> **Security:** Never commit this file to version control. It contains secrets.

The OpenAI API key can also be set at runtime via the Telegram bot command `/setapikey YOUR_KEY` or via the `OPENAI_API_KEY` environment variable.

---

## Quick Start

### 1. Launch the application

```bash
java -jar target/investpro-1.0.0-SNAPSHOT.jar
```

### 2. Connect with paper trading (recommended first step)

1. In the exchange selector, choose an exchange (e.g., **Binance US**)
2. Enable **Paper Trading** mode — no API keys required
3. A virtual balance is assigned automatically

### 3. Select a symbol and view live data

- Pick a trade pair from the Market Watch panel (e.g., `BTC/USDT`)
- WebSocket streams ticker, trades, and order book in real time
- Charts update automatically

### 4. Place a paper order

- Set quantity and order type (Market or Limit)
- Click **Buy** or **Sell**
- Confirm in the Order History panel

### 5. Enable a strategy

- Open **Strategy Engine** → select a strategy from the catalog
- Assign it to the active symbol
- Signals are generated and reviewed through the pipeline before any order fires

### 6. Set up Telegram alerts (optional)

1. Create a Telegram bot via [@BotFather](https://t.me/botfather) and copy the token
2. Set `telegram_token=YOUR_TOKEN` in config
3. Send `/start` to your bot to register your chat ID
4. Alerts for signals, fills, and errors are sent automatically

---

## Strategy Development

Implement the `TradingStrategy` interface to write your own strategy:

```java
package org.investpro.strategy.user;

import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.data.CandleData;

public class MyMomentumStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "MyMomentumStrategy";
    }

    @Override
    public StrategySignal evaluate(StrategyContext context) {
        CandleData latest = context.getLatestCandle();
        if (latest == null) return StrategySignal.NEUTRAL;

        // Your logic here
        if (latest.getClose() > latest.getOpen() * 1.005) {
            return StrategySignal.buySignal(context.getTradePair(), latest.getClose());
        }
        return StrategySignal.NEUTRAL;
    }
}
```

Register in `StrategyBootstrapper` or use `StrategyRegistry.register(new MyMomentumStrategy())`.

**Strategy pipeline safety gates** (cannot be bypassed):
1. `SignalToDecisionFilter` — validates signal against current market conditions
2. `BotTradeDecisionEngine` — institutional-grade decision evaluation
3. `RiskManagementSystem` — enforces position sizing and loss limits
4. `AiReasoningService` (if enabled) — optional AI review
5. `TradeExecutionCoordinator` — final execution gate

Auto-trading is **disabled by default**. Enable it explicitly via `SystemCore` after all validations pass.

---

## Risk Management Philosophy

InvestPro is built on the principle that **no signal should ever reach the market unchecked**.

The execution pipeline enforces:

| Control | Description |
|---|---|
| **Max risk per trade** | Configurable % of account balance per position |
| **Max daily loss** | Hard stop on cumulative daily losses |
| **Small-account mode** | Reduced unit sizes when balance is below threshold |
| **Symbol cooldown** | Prevents open+close in the same evaluation cycle |
| **AI reasoning gate** | Optional second opinion before execution |
| **Auto-trading off by default** | Must be explicitly enabled; never implied |

> **Strongly recommended:** Run any strategy in paper trading for a minimum of 2–4 weeks before enabling live trading. Monitor signal flow via `SignalMonitorService` logs.

---

## Roadmap

### v1.0 (Current — active development)
- [x] 8 exchange adapters with WebSocket streaming
- [x] Strategy engine + signal pipeline
- [x] Agent runtime (SmartBot + SymbolAgents)
- [x] Risk management system
- [x] AI reasoning integration (OpenAI)
- [x] Paper trading
- [x] Telegram bot + command handler
- [x] Docker + VNC deployment
- [ ] StrategyLab backtesting (in progress)
- [ ] Full test coverage

### v1.5 (Planned)
- [ ] Futures / perpetual swap support (Binance)
- [ ] Advanced backtesting: walk-forward, Monte Carlo
- [ ] Portfolio rebalancing tools
- [ ] Performance analytics dashboard (Sharpe, Sortino, max drawdown)
- [ ] User-defined strategy builder UI

### v2.0 (Future)
- [ ] REST API server for remote control / web clients
- [ ] DEX / DeFi protocol adapters
- [ ] Cloud sync and multi-machine agent coordination
- [ ] Mobile companion app
- [ ] AI-generated strategy suggestions

---

## Contributing

Contributions are welcome. Please follow these steps:

1. **Fork** the repository
2. **Create a branch** for your feature or bugfix:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Write code** following the existing style (Java 21, Google Java Style)
4. **Add or update tests** for changed behaviour
5. **Run the build** to verify nothing is broken:
   ```bash
   mvn clean package
   ```
6. **Open a Pull Request** with a clear description of what changed and why

### Code Standards
- Java 21, Maven build
- Lombok for boilerplate reduction
- SLF4J/Logback for all logging (no `System.out.println`)
- `@NotNull` / `@Nullable` JetBrains annotations on public API parameters
- Null-safe code; no silent NPE swallowing

### Reporting Issues
Use [GitHub Issues](https://github.com/nguemechieu/investpro/issues). Include:
- Java version (`java -version`)
- OS
- Steps to reproduce
- Relevant log lines from `~/.investpro/logs/`

---

## Troubleshooting

**`Unsupported class version` error**
```bash
# Install Java 21+
java --version
# Download from https://adoptium.net/
```

**WebSocket disconnects**
- Reconnection with exponential back-off is automatic (2s → 32s)
- Check `~/.investpro/logs/investpro.log`
- Verify firewall allows outbound WebSocket on port 443

**HTTP 429 / 418 rate limit**
- WebSocket streaming avoids most REST rate limits
- Automatic 65-second cooldown on REST rate-limit errors
- Prefer WebSocket mode; disable REST polling if not needed

**JavaFX display error on Linux / Docker**
```bash
export DISPLAY=:0
xhost +local:docker
docker run -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix ...
# Or use the provided docker-compose.yml with built-in VNC
```

**High latency to exchange**
Open **Tools → System Monitor** → Network tab to measure latency. Healthy: < 500 ms. Switch to a wired connection or a VPS closer to the exchange datacenter.

---

## Disclaimer

> **IMPORTANT — READ BEFORE USING**
>
> InvestPro is provided as-is, for educational, research, and development purposes.
>
> - **Trading and investing carry significant financial risk.** You can lose some or all of your capital.
> - **Past performance does not guarantee future results.** No backtested or paper-traded result implies live profitability.
> - **This software does not provide financial advice.** It is a tool, not a recommendation engine.
> - Always start with **paper trading**. Validate your strategy thoroughly before using real capital.
> - Use only capital you can afford to lose entirely.
> - Consult a qualified financial advisor before making any investment decisions.
>
> The author and contributors accept no responsibility for financial losses, missed opportunities, execution errors, or any other outcomes resulting from use of this software.

---

## License

Licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2022–2026 Noel Martial Nguemechieu

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## Contact & Community

| Channel | Link |
|---|---|
| **GitHub Issues** | [Report bugs and request features](https://github.com/nguemechieu/investpro/issues) |
| **GitHub Discussions** | [Ask questions and share ideas](https://github.com/nguemechieu/investpro/discussions) |
| **Email** | nguemechieu@live.com |

**Author:** Noel Martial Nguemechieu  
**Repository:** [https://github.com/nguemechieu/investpro](https://github.com/nguemechieu/investpro)  
**First commit:** December 2022

---

<p align="center">
  <sub>Built with Java 21 · JavaFX 21.0.6 · Apache Maven · Open source under Apache 2.0</sub>
</p>
]]>