# InvestPro User Guide

This guide explains the everyday setup steps for running InvestPro, connecting an exchange, enabling live market data, configuring notifications, and checking that fills are being received from the broker.

## 1. First Launch

1. Install Java 21 or newer.
2. Open a terminal in the InvestPro project folder.
3. Start the app:

```powershell
.\mvnw.cmd javafx:run
```

InvestPro reads configuration from these places, in order:

1. Java system properties.
2. Environment variables.
3. `.env`.
4. `config.properties` in the project folder or classpath.
5. Built-in defaults.

For private keys, prefer your local `config.properties` or environment variables. Never commit real keys or passwords.

## 2. Exchange Setup

Open **Settings** or edit `config.properties`, then enter the credentials for the exchange you use.

Common keys:

```properties
coinbase.key=
coinbase.secret=

oanda.token=
oanda.account_id=

binance.us.key=
binance.us.secret=
```

After saving credentials:

1. Select the exchange in the main toolbar.
2. Click connect or refresh.
3. Confirm Market Watch loads symbols from the connected exchange.
4. Confirm Account, Orders, Positions, and Broker Activity panels update.

Do not use a key that is restricted from order submission if you want live trading. Read-only keys can load balances and market data, but order placement will be rejected by the broker.

## 3. Market Watch And Symbol Agents

Market Watch is the source list for symbol agents.

When symbols load:

1. InvestPro seeds each symbol into `SymbolAgentManager`.
2. A `SymbolAgent` is registered once per symbol.
3. Live bid/ask ticks update symbol readiness.
4. Existing valid strategy assignments are reused.
5. Weak or expired assignments can be re-evaluated based on `investpro.strategy.reassignBelowScore`.

Useful strategy keys:

```properties
investpro.strategy.minStrategyScore=60.0
investpro.strategy.hardMinStrategyScore=40.0
investpro.strategy.reassignBelowScore=40.0
investpro.strategy.autoAssignBest=true
investpro.strategy.requireBacktestBeforeLive=true
investpro.strategy.requirePaperTradingBeforeLive=false
```

## 4. Live Trading Safety Checklist

Before enabling live trading:

1. The connected exchange must be the exchange you intend to trade.
2. The selected symbols must be broker-tradable.
3. API keys must allow order submission.
4. Kill switch must be off.
5. Market/session must be open for the instrument.
6. Margin and risk checks must pass.
7. Strategy assignment must exist for that symbol.
8. Broker fills must confirm execution before Trades/Performance treat a trade as filled.

AI review is advisory only. It cannot override risk rejection, exchange tradeability rejection, kill switch, margin guard, market-closed status, or unsupported pair status.

## 5. Broker Fills And Activity

InvestPro treats broker fills as the source of truth.

For exchanges that expose fill history or user streams, InvestPro:

1. Polls or receives account fills.
2. De-duplicates each fill by broker/trade identity.
3. Emits `FILL_UPDATE`.
4. Stores a normalized `BrokerActivityEvent`.
5. Projects the event into orders, trades, positions, account, and performance surfaces.

To verify fills:

1. Start streaming with **Safe Default** or **Everything** mode.
2. Place a small test order in paper or a safe live environment.
3. Open **Broker Activity** or logs.
4. Confirm a fill event appears with exchange, pair, side, quantity, price, fee, and event time.
5. Confirm Trades and Performance use actual fill price, not requested order price.

## 6. Email Notifications

InvestPro supports SMTP email alerts.

You can configure email in **Settings > Email Notifications** or in `config.properties`.

### Required Fields

```properties
EMAIL_NOTIFICATIONS_ENABLED=true
EMAIL_SMTP_HOST=smtp.example.com
EMAIL_SMTP_PORT=587
EMAIL_SMTP_USERNAME=your_email@example.com
EMAIL_SMTP_PASSWORD=your_app_password
EMAIL_SMTP_STARTTLS=true
EMAIL_FROM=your_email@example.com
EMAIL_TO=alerts@example.com
```

Field meaning:

- `EMAIL_SMTP_HOST`: outgoing SMTP server from your email provider.
- `EMAIL_SMTP_PORT`: usually `587` for STARTTLS or `465` for SSL/TLS.
- `EMAIL_SMTP_USERNAME`: usually your full email address.
- `EMAIL_SMTP_PASSWORD`: usually an app password, not your normal login password.
- `EMAIL_SMTP_STARTTLS`: use `true` for port `587`. For port `465`, InvestPro automatically uses implicit SSL/TLS.
- `EMAIL_FROM`: sender address.
- `EMAIL_TO`: destination for alerts.

### Gmail

Use:

```properties
EMAIL_SMTP_HOST=smtp.gmail.com
EMAIL_SMTP_PORT=587
EMAIL_SMTP_USERNAME=yourname@gmail.com
EMAIL_SMTP_PASSWORD=your_16_character_app_password
EMAIL_SMTP_STARTTLS=true
EMAIL_FROM=yourname@gmail.com
EMAIL_TO=alerts@example.com
```

Where to get the password:

1. Turn on 2-Step Verification for the Google account.
2. Go to Google Account security settings.
3. Create an App Password for Mail or for a custom app named InvestPro.
4. Paste the generated app password into InvestPro.

Google also supports SSL on port `465`. If you use port `465`, keep TLS enabled in the UI; InvestPro will use implicit SSL automatically.

### Outlook.com / Microsoft 365

Use:

```properties
EMAIL_SMTP_HOST=smtp.office365.com
EMAIL_SMTP_PORT=587
EMAIL_SMTP_USERNAME=yourname@outlook.com
EMAIL_SMTP_PASSWORD=your_password_or_app_password
EMAIL_SMTP_STARTTLS=true
EMAIL_FROM=yourname@outlook.com
EMAIL_TO=alerts@example.com
```

Where to get the password:

1. For personal Outlook accounts, use the account password if SMTP AUTH is allowed.
2. If multi-factor authentication is enabled, create an app password from Microsoft account security settings when available.
3. For Microsoft 365 business accounts, ask the tenant admin to allow authenticated SMTP for the mailbox if it is disabled.

### Yahoo Mail

Use:

```properties
EMAIL_SMTP_HOST=smtp.mail.yahoo.com
EMAIL_SMTP_PORT=465
EMAIL_SMTP_USERNAME=yourname@yahoo.com
EMAIL_SMTP_PASSWORD=your_yahoo_app_password
EMAIL_SMTP_STARTTLS=true
EMAIL_FROM=yourname@yahoo.com
EMAIL_TO=alerts@example.com
```

Where to get the password:

1. Open Yahoo account security.
2. Enable two-step verification if required.
3. Generate an app password for a third-party mail app.
4. Paste that app password into InvestPro.

### Custom Domain Or Business Email

Ask your email host for:

1. SMTP server/host.
2. SMTP port.
3. Encryption type: STARTTLS or SSL/TLS.
4. Username format.
5. Whether an app password is required.

Most providers use one of these:

```text
Port 587 = STARTTLS
Port 465 = SSL/TLS from connection start
```

### Fix: Could Not Convert Socket To TLS

This means the SMTP server and TLS mode do not match.

Try these fixes:

1. If using port `587`, enable TLS/STARTTLS.
2. If using port `465`, use SSL/TLS. InvestPro now detects this automatically.
3. Confirm the SMTP host is the outgoing SMTP host, not IMAP/POP.
4. Use an app password when the provider requires it.
5. Check whether antivirus, VPN, firewall, or router blocks SMTP ports.
6. For Microsoft 365, confirm SMTP AUTH is enabled for the mailbox.

## 7. Telegram Notifications

1. Create a bot with BotFather.
2. Copy the bot token.
3. Put it in Settings or config:

```properties
TELEGRAM_ENABLED=true
TELEGRAM_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

4. Send `/start` to the bot.
5. Use the test button in Settings.

## 8. OpenAI Trade Review

OpenAI is optional. If unavailable, InvestPro uses local fallback review.

```properties
ai.openai.enabled=true
ai.openai.apiKey=
ai.openai.endpoint=https://api.openai.com/v1/chat/completions
ai.openai.model=gpt-5
ai.openai.temperature=0.1
ai.openai.strictJson=true
```

Use low temperature for trading review. AI does not place orders directly.

## 9. Troubleshooting

No symbols in Market Watch:

- Refresh the exchange.
- Check API credentials.
- Check internet connection.
- Check whether the selected exchange supports the instrument type.

No live trades:

- Confirm live trading mode is enabled.
- Confirm API key allows order submission.
- Check broker tradeability status.
- Check System Operations warnings.
- Check strategy assignment for the symbol.

No fills:

- Use Safe Default or Everything streaming mode.
- Confirm the broker exposes account fill history.
- Confirm API key has private account/fill permissions.
- Check Broker Activity for normalized fill events.

Email fails:

- Confirm SMTP host, port, username, and app password.
- Use port `587` with STARTTLS or port `465` with SSL/TLS.
- If the error mentions TLS, try the alternate port.
- Check provider security settings.

