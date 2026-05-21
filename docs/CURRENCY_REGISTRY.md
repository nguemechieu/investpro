# Currency Registry In InvestPro

## Why This Exists

InvestPro consumes symbols from many exchanges and asset classes (forex, crypto, metals, indices, stocks).
Exchanges can introduce new symbols at any time. A strict static list can fail at runtime and break charting,
market-watch, or bot pipelines.

`CurrencyRegistry` provides a single in-memory lookup point with O(1) access and graceful unknown handling.

## ServiceLoader Scope

ServiceLoader is used only for grouped provider discovery:

- `FiatCurrencyProvider`
- `CryptoCurrencyProvider`
- `MetalCurrencyProvider`
- `IndexCurrencyProvider`

Individual currencies (USD, EUR, BTC, etc.) are **not** discovered with ServiceLoader.
Providers return bulk currency collections once during registry load.

## Startup And Performance Model

- Providers are loaded once by `CurrencyRegistry.loadDefault()` / `CurrencyRegistry.global()`.
- Currencies are cached in maps (`byCode`, `bySymbol`) for constant-time lookup.
- No ServiceLoader calls should happen in chart render loops, ticker updates, strategy scans, or order execution.

## Unknown Currency Safety

If an exchange returns an unseen code, `CurrencyRegistry.findOrUnknown(code)` returns `UnknownCurrency`.

- prevents runtime crashes
- uses the code as display symbol
- defaults to high precision (8 fractional digits)
- logs unknown code once per runtime

## Icon Strategy

Default icon paths are computed by type:

- fiat: `/icons/currencies/{code}.svg`
- crypto: `/icons/crypto/{code}.svg`
- metals: `/icons/metals/{code}.svg`
- indices: `/icons/indices/{code}.svg`

If resource lookup fails, fallback is `/icons/currencies/default.svg`.

`CurrencyIconLoader` adds image caching and safe JavaFX fallback nodes.

## How To Add A New Provider

1. Implement `org.investpro.models.currency.spi.CurrencyProvider`.
2. Return a stable provider id and supported currency groups.
3. Return currencies in `getCurrencies()`.
4. Register provider class in:
   `src/main/resources/META-INF/services/org.investpro.models.currency.spi.CurrencyProvider`

## Exchange Adapter Guidance

Adapters should resolve symbols with registry-backed parsing:

- use `TradePair.fromSymbol("BTC-USD")` or equivalent
- use `CurrencyRegistry.findOrUnknown(code)` for base/quote
- preserve exchange-native symbol in `TradePair.nativeSymbol`

Tradability decisions remain owned by `UniversalTradabilityService` / exchange capability checks.
