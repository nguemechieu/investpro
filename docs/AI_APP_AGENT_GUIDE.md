# Shared Telegram and desktop app agent

Telegram and the AI Interaction panel use the same application-owned assistant.
It remains available while the trading bot is stopped. Configure OPENAI_API_KEY
and the Telegram bot token through the existing app configuration. Remote users
must be authorized through TELEGRAM_ALLOWED_USER_IDS and, when configured,
TELEGRAM_ALLOWED_CHAT_IDS.

The assistant can inspect available venues, native instrument symbols, account
balances, local paper or live positions, open orders, quotes, candles and order
books. It can combine several data requests before explaining its analysis.
Snapshots include the venue, execution mode and timestamps. Quote responses
include live/delayed/frozen status. Data access remains subject to the exchange's
connection, contract resolution and subscription permissions.

Examples for either interface:

- "Compare the current BTC/USD spread on my connected exchanges."
- "Analyze recent hourly candles for BIP-20DEC30-CDE on Coinbase."
- "Review my balances, positions and pending orders for concentration risk."
- "Open the BTC/USD chart and set its timeframe to 3600 seconds."
- "Prepare a limit buy for 0.01 BTC/USD at 50000 on my selected exchange."
- "Pause my bot."

The agent uses the existing app command and execution layers for market, limit,
stop, bracket and trailing orders, cancellations, bot controls, risk and strategy
reports, and chart open/timeframe/refresh/zoom controls. Mutations target the
selected desktop exchange. Trade previews retain their exchange/mode binding,
expiration and explicit user /confirm CODE; the model cannot confirm its own
preview. Chart requests are asynchronous and report that they were requested,
rather than claiming success before the desktop completes them.

Direct data commands are also available:

- /venues
- /data coinbase account
- /data coinbase positions
- /data coinbase orders
- /data coinbase symbols BTC
- /data coinbase quote BTC/USD
- /data coinbase candles BTC/USD 3600 60
- /data coinbase orderbook BTC/USD

Use the venue IDs returned by /venues. Candle results are limited to 100 bars;
list results are bounded, and tool requests have limits. Unavailable data is
reported rather than invented. Credentials and personal account profiles are
excluded from data projections. Screenshot analysis continues to run without
action tools. The agent operates through exposed InvestPro APIs; it does not
have arbitrary operating-system or credential access.

# News analysis

The Telegram and desktop assistants can retrieve RSS search results with
`/news SCHW stock 10`, `/news BTC crypto 10`, or `/news EUR/USD forex 10`.
You can also ask “What is the latest news about Charles Schwab?” in ordinary language.
No connected exchange or running trading bot is required for news retrieval.
The assistant uses returned headlines, summaries, publisher names, publication dates,
and source links to assess possible catalysts and risks. Results may be cached for
five minutes and RSS coverage is not exhaustive or a real-time subscription.
Empty or failed searches are reported explicitly; the assistant must not invent news.
