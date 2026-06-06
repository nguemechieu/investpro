# Market Instruments

InvestPro keeps `TradePair` and `MarketInstrument` separate.

`TradePair` is a base/quote price pair such as `BTC/USD` or `EUR/USD`. It remains the compatibility model for spot charts, existing order panels, and legacy exchange APIs.

`MarketInstrument` is the full exchange product identity. It preserves the native product id, venue, product type, expiry type, underlying type, optional `TradePair`, tradability state, and raw exchange metadata. Coinbase derivative products such as `BTC-PERP` or `BIT-28JUL23-CDE` must keep their native `product_id` and must not be rewritten into spot-style symbols.

## Coinbase Fields

Coinbase products can include:

- `product_type`: currently `SPOT` or `FUTURE`
- `contract_expiry_type`: for futures, `EXPIRING` or `PERPETUAL`
- `futures_underlying_type`: for futures, `SPOT`, `INDEX`, `EQUITY`, `COMMODITY`, or `FX`
- `future_product_details`: venue, contract code, contract size, expiry, and descriptive fields

Classification rules:

- `SPOT` maps to `MarketType.SPOT`
- `FUTURE` plus `contract_expiry_type=PERPETUAL` maps to `MarketType.PERPETUAL`
- `FUTURE` plus `futures_underlying_type=INDEX` maps to `MarketType.INDEX`
- `FUTURE` plus `futures_underlying_type=EQUITY` maps to `MarketType.EQUITY`
- `FUTURE` plus `futures_underlying_type=COMMODITY` maps to `MarketType.COMMODITY`
- `FUTURE` plus `futures_underlying_type=FX` maps to `MarketType.FX`
- Unknown or missing values map to `UNKNOWN`

## Market Watch

Market Watch can display restricted or view-only products when market data is allowed. The product filter can show spot, futures, perpetuals, indices, stocks, commodities, FX, tradable-only, and restricted-only instruments.

Restricted products are not hidden by default because they are useful for analysis and backtesting. They remain disabled for live/bot trading unless tradability and execution routing allow them.

## Bot Scanner

The bot scanner should consume `MarketInstrumentService.loadForExchange(exchange)` rather than repeatedly calling the exchange product endpoint. The service caches by exchange/native symbol with a TTL. Final live-order permission must still be rechecked through `UniversalTradabilityService`.

Bot trading candidates must satisfy:

- the instrument is not `UNKNOWN`
- market data is available
- tradability allows bot trading and order submission
- the selected strategy supports the instrument market type
- the execution route is implemented

## Execution

`ExecutionEngine` keeps existing `TradePair` order methods for compatibility. The `MarketInstrument`-aware overload prevents unsafe routing:

- `SPOT` can continue through spot order routes when tradability passes
- `FUTURE`, `PERPETUAL`, `INDEX`, `EQUITY`, `COMMODITY`, and `FX` futures are blocked until derivatives order routing is implemented
- `UNKNOWN` is always blocked for live execution

This prevents futures and perpetual products from accidentally being submitted through spot order code.
