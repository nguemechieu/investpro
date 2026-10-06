# Exchange stream access

> Current documentation baseline (2026-10-05): JDK 27, JavaFX 27 and Maven 3.8.5+. Use the [documentation index](../docs/README.md) for current setup, trading-desk behavior, Telegram commands and release limits. Design examples below are not proof of broker support or deployment verification.

`Exchange.canUseCapability(ExchangeFeature)` combines the existing `ExchangeCapability`
profile with the adapter's current `hasPrivateAuthentication()` state. Unsupported
features remain unavailable even when authentication is configured. Public market
features only require authentication when the profile explicitly requires it.
`getAccessMode()` exposes `PUBLIC_DATA_ONLY` or `AUTHENTICATED`; this describes account
access, independently of instrument tradability and the selected trading mode.

The default authentication implementation reuses `checkAuthentication()`. Adapter
implementations must report current configuration without making network calls.
Coinbase overrides it with the same predicate used by its private endpoint guard:
a configured JWT signer or the existing prebuilt bearer token path, in live mode.
`requirePrivateEndpointAuth()` remains the final enforcement at the endpoint.

`PollingExchangeStreamer` checks access before creating account/balance, order/fill,
or position tasks. It emits one INFO message per skipped feature until access becomes
available. Disabled subscriptions allocate neither an executor nor scheduled tasks.
Public ticker and order-book paths retain their existing behavior. Actual failures
from running tasks still reach the consumer and desktop bridge.

Account/balance subscriptions share one account poller, and order/fill subscriptions
retain their existing shared order poller. There is no dedicated fill or order-history
poller. Adding one must use the same feature/authentication gate. Shared cancellation
semantics remain unchanged. `stopAll()` cancels all tasks and shuts down the lazy
executor; a subsequent subscription creates a fresh executor for reconnection.

`LIMIT_ONLY` and `POST_ONLY` remain distinct tradability statuses. Existing filtering
is unchanged; supporting restricted execution requires a separate, tested change.

Regression coverage in `PollingExchangeStreamerTest` verifies public discovery with
mocked HTTP, unauthenticated live/paper gating, public streaming, signer/bearer access,
unsupported positions, shutdown/restart, one-time logging, and unexpected errors.
Server acceptance, permissions, and bearer expiration remain endpoint concerns.
