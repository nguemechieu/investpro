# Assistant and market organization

InvestPro owns one `AssistantRuntime` for the application. It starts when the app
starts, remains available while trading is stopped or the trading screen is
hidden, and closes when the application exits. Trading cores borrow its Telegram
notifier for commands and trading notifications; starting/stopping trading does
not start/stop message polling.

`InvestorAssistantService` owns conversation history and OpenAI Responses API
requests. Desktop answers consume streaming response events. Telegram uses four
background workers with a bounded queue, so a slow answer does not block polling
or other users. A conversation accepts one Telegram request at a time to preserve
command order. Desktop and each authorized Telegram user have separate histories.

Open **AI → AI Interaction** for the conversational panel. It includes suggested
questions, streamed answers, a question composer, and a new-conversation action.
Read-only account/selected-market snapshots can accompany relevant questions;
missing data is explicitly identified. The assistant cannot execute orders.

Configuration uses the existing environment/`.env` loader and onboarding settings:

- `OPENAI_API_KEY`: required for AI answers.
- `TELEGRAM_BOT_TOKEN`: required for Telegram polling.
- `TELEGRAM_ALLOWED_USER_IDS`: authorized numeric Telegram user IDs.
- `TELEGRAM_ALLOWED_CHAT_IDS` / `TELEGRAM_CHAT_ID`: optional chat restrictions and notification destination.
- `TELEGRAM_OPENAI_MODEL`: existing model override; the existing default is retained.

## Market structure and underlying assets

Onboarding and Market Watch separate three dimensions:

| Dimension | Examples |
| --- | --- |
| Market | Spot, Derivatives |
| Contract | Cash, perpetual, future, option, CFD, forward, swap |
| Underlying asset | Crypto, FX, equity, ETF, index, commodity, metal, bond, fund |

An index future has a **future contract** and an **index underlying**. FX and bonds
are not intrinsically derivatives: cash/spot instruments and derivative contracts
remain distinct. Unknown metadata remains unknown. Legacy enum values and saved
configurations are supported; new selections store underlying assets separately.

Coinbase products keep their exchange-native IDs, for example `BTC-PERP-INTX` and
`BTC-25SEP26-CDE`, in the display, quote requests and cached asset restoration.
Product/expiry metadata identifies contracts even when an ID resembles a currency
pair. Explicit venue metadata takes priority over suffix fallback; international
perpetuals are not automatically tagged as US CDE products. Contracts sharing an
underlying but having different expiries remain separate instruments.

Market browsing does not grant execution permissions. Existing account eligibility,
tradability and unsupported-derivatives execution gates remain in place.

References: [OpenAI response streaming](https://developers.openai.com/api/docs/guides/streaming-responses)
and [Coinbase product metadata](https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/products/get-product).
