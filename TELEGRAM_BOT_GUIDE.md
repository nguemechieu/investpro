# Telegram bot guide

Updated: 2026-10-05. The complete supported command reference and setup instructions
are maintained in [Telegram remote desk](docs/telegram-remote-desk.md).

Set `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_IDS`, `TELEGRAM_CHAT_ID` and,
for AI questions, `OPENAI_API_KEY` in the application's environment. Start the
System Agent, then send `/start` to the bot in an authorized private chat.
Without an allowed-user list, incoming requests are denied. Group commands are denied.

| Category | Examples |
|---|---|
| Account | `/status`, `/balance`, `/portfolio`, `/positions`, `/orders`, `/history` |
| Markets | `/quote BTC/USD`, `/analyze BTC/USD`, `/watch BTC/USD`, `/watchlist` |
| Orders | `/buy BTC/USD 0.01`, `/sell BTC/USD 0.01`, `/limit buy BTC/USD 0.01 50000` |
| Confirmation | `/confirm CODE`, `/abort`, `/cancel ORDER_ID` |
| Controls | `/pause`, `/resume`, `/mode`, `/exchange`, `/health`, `/screenshot` |
| Questions | `/ask QUESTION`, `/invest TOPIC`, `/compare ASSETS`, `/learn TOPIC`, `/news TOPIC` |
| Risk sizing | `/size 10000 1 100 95` |
| Conversation | Plain text questions, `/reset`, `/help` |

Order actions and resume require a one-use confirmation within 60 seconds. PAPER
uses local execution; LIVE requires the desktop broker connection. Authorized
users share the desktop account, with separate conversations and watchlists.

`/toggleauto` and `/pnl` are not supported commands. `/setapikey` directs users to
configure the desktop environment; it no longer accepts API keys from Telegram.
AI answers cannot execute trades. `/news` does not fetch current headlines.

Notifications go to the configured target chat. Screenshots are available there
only; image upload runs outside the JavaFX thread. Use one running app instance
per Telegram bot token. Live end-to-end verification is still required after setup.
