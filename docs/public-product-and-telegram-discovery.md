# Product and notification discovery

Telegram notification destinations are extracted from authorized `getUpdates` messages.
`TELEGRAM_CHAT_ID` is a preference that must match a discovered chat; the app no longer assumes that an allowed user ID is a chat ID.
Send a message to the bot to make the chat visible to Telegram. Existing user/chat allowlists still apply.
Polling and discovery share one request gate and update offset so discovery does not discard commands or create a competing poller.
An established destination is reused; no extra discovery request is necessary for every notification.

Coinbase product discovery fetches public spot and derivative catalogs before authenticated account catalogs.
Public derivative products remain view-only; authenticated product metadata replaces matching public records when available.
Market Watch defaults to market-data visibility, while the Tradable Only filter remains available.
Private permission failures still describe account trading entitlement; public visibility does not grant live order access.
If Coinbase does not publish contracts to the public endpoint, cached verified listings remain the existing fallback.

Use exchange-issued product IDs such as `GOL-25NOV26-CDE` or `BIP-20DEC30-CDE`.
Do not generate contract IDs by appending dates to spot assets: contract roots, expiry years and schedules differ.
The local asset catalog should contain discovered contracts, not invented futures or perpetual products.

References: [Coinbase public products](https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/public/list-public-products)
and [Telegram getUpdates](https://core.telegram.org/bots/api#getupdates).
