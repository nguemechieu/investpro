# Telegram remote desk

Run InvestPro and start its System Agent to start the Telegram bot. Use one running
InvestPro instance per bot token. The desktop exchange and trading mode are the
source of truth for every remote action.

## Setup

Create a bot with Telegram's BotFather, then set these environment variables before
starting InvestPro. Keep secrets out of Git and Telegram messages.

```text
TELEGRAM_BOT_TOKEN=<bot token>
TELEGRAM_ALLOWED_USER_IDS=<your numeric Telegram user ID>
TELEGRAM_CHAT_ID=<your private chat ID, normally the same as your user ID>
OPENAI_API_KEY=<OpenAI API key>
```

For several authorized users, separate their numeric IDs with commas. Optional
`TELEGRAM_ALLOWED_CHAT_IDS` restricts which private chats can access the bot.
Without authorized user IDs, incoming commands and AI questions are denied.
Group chats cannot access the remote desk. All authorized users control the same
desktop account; this is not a separate brokerage account for each Telegram user.
Watchlists, confirmation codes and AI conversations are isolated by chat and user.

The same settings can be configured in InvestPro's properties:

```properties
telegram_token=<bot token>
telegram.allowed_user_ids=123456789
telegram.allowed_chat_ids=123456789
telegram.chat_id=123456789
telegram.openai_model=gpt-4.1-mini
openai.api_key=<OpenAI API key>
```

The model is configurable and must be available to your OpenAI API project.
Properties take precedence over environment variables. Do not use `/setapikey`;
the bot directs users to configure credentials in the desktop environment.
Send `/start` in a private chat; the bot also installs a Telegram command menu.

## Commands

| Area | Commands |
|---|---|
| Account | `/status`, `/balance`, `/portfolio`, `/positions` |
| Orders | `/orders`, `/history` (last seven days, up to 30 orders) |
| Quotes | `/quote BTC/USD`, `/market BTC/USD` |
| Watchlist | `/watch BTC/USD`, `/unwatch BTC/USD`, `/watchlist` |
| Market analysis | `/analyze BTC/USD` (quote supplied to AI) |
| Buy/sell | `/buy BTC/USD 0.01`, `/sell BTC/USD 0.01` |
| Limit orders | `/limit buy BTC/USD 0.01 50000` |
| Cancel order | `/cancel ORDER_ID` (IDs appear in `/orders`) |
| Confirmation | `/confirm CODE`, `/abort` |
| Automation | `/pause`, `/resume` (resume requires confirmation and system health checks) |
| Desktop state | `/mode`, `/exchange`, `/health`, `/strategy`, `/risk` |
| Screenshot | `/screenshot` (configured notification chat only) |
| Risk sizing | `/size 10000 1 100 95` |
| Investment questions | `/invest TOPIC`, `/compare ASSETS`, `/learn TOPIC`, `/news TOPIC` |
| Conversation | `/ask QUESTION`, plain text questions, `/reset` |
| Help | `/help`, `/start` |

`/size` calculates equity × risk percentage ÷ absolute entry/stop distance.
It assumes linear pricing and excludes fees, gaps, leverage and contract multipliers.
Quantities are base-asset units, not dollar amounts. Broker product restrictions,
minimums, precision and supported order types still apply.

Trading and cancellation commands first create a preview. Confirmation codes expire
after 60 seconds and can be used once by the requesting user. A changed exchange or
mode invalidates the preview. An uncertain submission is not automatically retried;
check `/orders` and `/history` before submitting another request.

PAPER routes to InvestPro's session-local simulator, without submitting broker orders.
Its market orders use fetched quotes; pending limit orders do not yet have a matching
engine. LIVE requires the desktop broker connection and authentication. `/pause`
disables automatic trading but does not cancel existing orders.

## AI and operational limits

OpenAI receives your question, recent conversation turns, exchange/mode context and,
for `/analyze`, the fetched quote. Account data is not automatically sent with ordinary
questions. The AI uses the Responses API with `store=false` and has no execution tools.
The last four successful exchanges are retained in memory until `/reset` or restart.

AI answers cannot place orders or change settings. `/news` answers questions about
news supplied by the user; it has no live news feed or web search and must not invent
current headlines. Watchlists are on-demand quote lists, not price alerts.

Commands and AI requests run on the background polling thread. Replies are split into
Telegram-sized plain text chunks. Requests are rate limited per user; wait briefly
between messages. Watchlists and paper trades are session-local. Notifications go to
the configured notification chat, not automatically to every authorized user.

Implementation references: [Telegram Bot API](https://core.telegram.org/bots/api) and
[OpenAI Responses API](https://developers.openai.com/api/reference/python/resources/responses/methods/create).
