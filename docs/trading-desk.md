# Trading desk user guide

Updated: 2026-10-05. Desktop baseline: JDK 27 and JavaFX 27.

## Choose an exchange

Manual desk orders use live execution when the selected exchange has a validated
live account session. Without that authentication, orders, balances and history
use the local paper simulator. Public quotes or an open WebSocket do not establish
private authentication. The status bar displays the current execution route.

The simulator starts with USD 10,000 and remains session-local. Market orders use
a supplied quote. Pending limit/stop-style orders currently have no matching engine.

PAPER and LIVE selection applies only to bot trading, through the Bot mode control.
Bot PAPER uses local execution even when the desk is authenticated. Bot LIVE requires
a validated live account session and never silently falls back to paper execution.
Changing bot mode does not reconnect the broker or change manual order routing.

An exchange change preserves previously connected sessions. Credentials are requested
for a venue without a usable validated session. Reconnect from the desk if needed.

## Coinbase credential input

Supply the CDP key name and matching PEM private key. The normalizer accepts
supported JSON `{name, privateKey}` input, escaped newlines, quoted environment
assignments and single-line PEM input. It preserves key data while adapting its
format; missing PEM contents or mismatched credentials cannot be repaired.

Environment configuration uses `COINBASE_KEY_NAME` and `COINBASE_PRIVATE_KEY`.
Legacy API-key/secret aliases are fallback inputs, not a different credential type.
Do not place actual private keys in documentation, screenshots or public logs.

## Chart timeframe and detached windows

Use the chart header's timeframe selector. Options come from the exchange candle
supplier; unavailable intervals are rejected. The selected interval rebuilds the
chart and keeps the selector synchronized. `1m` is a minute, `1M` is a month,
and `8h` is 28,800 seconds.

When a chart is detached, its original tab remains with a placeholder. Use
**Reattach** in either the detached toolbar or the original tab. Closing the
detached stage also restores the chart to its original tab. Reattach removes the
chart from the detached root before installing it in the tab; repeated requests
are ignored. Chart replacement and window operations run on the JavaFX thread.

## Market Watch

The System Agent Market Watch shows symbol state, quotes, strategy, timeframe,
score, readiness and tradability metadata. Filters include product type,
tradable symbols, favorites and open sessions. Sorting supports symbol, strategy
score, spread and live readiness; column headers also support sorting.

Right-click a selected row to toggle its favorite state. Favorites last for the
window's lifetime. Row tooltips expose restrictions, product health and issues.
Refresh reloads instrument metadata as well as symbol state. Pause/Resume controls
automatic panel refresh. CSV export writes visible rows to the user's Downloads
directory. Wide tables scroll horizontally, and controls wrap in narrow windows.

Tradability is based on available metadata; missing or stale metadata must not be
treated as a guarantee that a live order will be accepted.

## Navigation and diagnostics

Use the navigation window to open application panels and exchange workspaces.
Subwindows are fitted to screen bounds; content wrappers and horizontal scrolling
allow access to larger panels. If content still clips, record the screen size,
display scaling, panel and steps to reproduce it.

The shared JavaFX stylesheet supplies concrete base/background paints for scroll
panes. Dialogs receive the application styles. A CSS warning should be investigated
with the affected view and stylesheets rather than dismissed as a successful render.

See [Telegram remote control](telegram-remote-desk.md), [exchange access](exchange-stream-access.md),
[release readiness](../PRODUCTION_READY.md), and [documentation index](README.md).

Market configuration carries an immutable broker parameter map. IBKR onboarding passes host, ports, authentication mode and client ID into dashboard adapter creation. Desk adapters connect to the live venue; bot mode controls bot execution independently. Delayed account validation is ignored when its exchange is no longer selected.
