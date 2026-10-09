# Charles Schwab browser authorization

InvestPro reuses the existing `Schwab` exchange/provider and central `AppConfig` lookup. It uses an authorization-code flow, not brokerage username/password authentication. One reusable HTTP client serves both OAuth and authenticated REST calls. Broker tasks use four workers with a bounded queue; background agents never open a browser.

## Documentation verification

The [official Schwab authentication guide](https://developer.schwab.com/user-guides/get-started/authenticate-with-oauth) confirms three-legged OAuth, HTTPS callback registration, client credentials and bearer-token access. The detailed Trader API product documentation was not readable through the documentation tools used for this implementation. Therefore **the Trader authorization endpoint, HTTP Basic client authentication, refresh-token lifetime and current account/order schemas still require verification against your approved app's official product documentation before deployment**. No real-account consent, request or trade has been tested.

`SCHWAB_AUTHORIZATION_URL` has no guessed default. The token URL retains the previous adapter's `https://api.schwabapi.com/v1/oauth/token` default and supports an explicit `SCHWAB_TOKEN_URL` override. This retained default and the existing adapter's HTTP Basic mechanism are not a claim of fresh official verification. Access expiration comes from `expires_in`; there is no assumed 30-minute lifetime. Refresh expiration uses `refresh_token_expires_in` if supplied; otherwise it is unknown and server rejection requires browser reauthorization. The implementation does not assume that rotation extends the consent lifetime or invent a seven-day expiry.

## Setup

1. Register a Trader API app in the [Schwab Developer Portal](https://developer.schwab.com/). Enable the required API products and wait for approval.
2. Obtain the app's client ID and client secret. Verify the current OAuth endpoints and client authentication requirements in its official documentation.
3. Register the **exact** HTTPS callback, including its port and path. This desktop implementation supports a loopback host (`127.0.0.1`, `localhost`, or IPv6 loopback); a remote domain requires a separate authenticated callback relay, which is not implemented.
4. Provision a PKCS12 TLS keystore whose certificate contains the callback hostname/IP in its subject alternative names. The browser must trust its certificate. InvestPro does not disable certificate checks or install trust automatically. Verify that Schwab accepts your registered loopback callback and certificate arrangement.
5. Supply configuration through the existing application configuration/environment support. Resolution is JVM property, environment, then `.env`. Keep secrets outside version control. Prefer a private local token directory outside OneDrive or other cloud synchronization.

Example placeholders only:

```dotenv
SCHWAB_CLIENT_ID=your_developer_app_client_id
SCHWAB_CLIENT_SECRET=your_developer_app_client_secret
SCHWAB_REDIRECT_URI=https://127.0.0.1:8443/schwab/callback
SCHWAB_AUTHORIZATION_URL=https://official-authorize-endpoint-from-your-approved-app.example/authorize
SCHWAB_TOKEN_URL=https://official-token-endpoint-from-your-approved-app.example/token
SCHWAB_CALLBACK_KEYSTORE=C:/private/investpro/schwab-callback.p12
SCHWAB_CALLBACK_KEYSTORE_PASSWORD=your_keystore_unlock_password
SCHWAB_TOKEN_STORE_PASSWORD=your_separate_long_random_unlock_password
# Optional: use a private local directory on the same filesystem for atomic replacement.
SCHWAB_TOKEN_PATH=C:/private/investpro/schwab.tokens
# Required to select an account when consent authorizes more than one account.
# May be an authorized account number or hash; REST order paths always use the returned hash.
SCHWAB_ACCOUNT_ID=your_selected_authorized_account_identifier
```

The `.example` OAuth URLs are deliberately nonfunctional placeholders. Copy the real values from the official documentation; do not run with these placeholders. The token-store password must contain at least 16 characters and should be a long random value. Losing it requires unlinking and reauthorizing; it is not stored beside the ciphertext.

For local development, a TLS certificate can be generated using the JDK `keytool` with `-storetype PKCS12`, appropriate `-ext SAN=...`, and a password entered interactively. Arrange browser trust explicitly. A self-signed certificate is not automatically suitable for Schwab registration or production.

6. Start InvestPro, select Schwab in onboarding, and choose **Connect Schwab**. On an existing desk, select Schwab and open **Settings → Schwab Connection**.
7. Complete Schwab's own browser login and account consent. No brokerage login fields are supplied by InvestPro.
8. The loopback listener validates the random, single-use state and authorization code, returns a generic browser message, and asynchronously exchanges the code. The listener times out after three minutes and closes on completion, cancellation or disconnect.
9. Tokens are encrypted and saved before the session becomes usable. Authorized account identifiers are then fetched; the trading desk reuses the authenticated onboarding instance.

## Storage, refresh and disconnect

`SchwabEncryptedTokenStore` encrypts access/refresh tokens and absolute expiration timestamps with AES-256-GCM, a fresh salt and nonce, and PBKDF2-HMAC-SHA256 (210,000 iterations). The separate unlock password is required at runtime. Temporary files have owner-only POSIX permissions or a Windows owner ACL and are atomically moved into place. Unsupported permissions or atomic replacement fail closed. Corruption, incorrect passwords and malformed token state require intervention; plaintext fallback is never used.

The default token path is `~/.investpro/schwab/<client-and-account-partition>.tokens`. Changing the app ID or configured account selects a different partition. No token is migrated from the old plaintext `SCHWAB_REFRESH_TOKEN` setting. Authorize once in the browser to populate the new encrypted store. This store serializes threads in one application process; do not run multiple InvestPro processes against the same token file, because rotating refresh tokens are not coordinated across processes.

Access tokens become stale 90 seconds before their reported expiry. Concurrent callers share one refresh future; cancellation of an individual dependent future does not cancel refresh for the others. Rotated refresh tokens are persisted before publication. Disconnect invalidates the generation so late refresh/authorization completions cannot restore the session.

- **Connect:** load saved authorization, refresh if necessary, then verify authorized accounts. The explicit UI action opens browser consent when authorization is required. Automatic exchange reconnects never launch a browser.
- **Reauthorize:** explicitly start a new browser flow.
- **Disconnect:** clear runtime tokens and stop pending consent; retain the encrypted file for later reconnect.
- **Unlink:** disconnect and delete the saved token file. This removes local authorization, not the Schwab-side OAuth grant. Revoke consent through Schwab if desired; no unverified revocation endpoint is invoked.

## REST failures and broker limitations

All protected REST requests carry `Authorization: Bearer ...` over the configured HTTPS origin. Redirects are disabled. Tokens are never placed in request URLs. Response bodies, credentials, authorization codes and token contents are not logged. Token/config `toString()` methods redact secrets, and callback error descriptions are not echoed.

- **401:** refresh once and retry once. A second 401 requires browser reauthorization. Concurrent responses for an older token reuse an already refreshed token.
- **403:** surface account/product/permission denial without refreshing or looping.
- **429:** shared pacing, a shared server pause, bounded GET retries, exponential backoff and jitter. Honor both numeric and HTTP-date `Retry-After`. Long server pauses fail promptly while keeping the pause active. Orders are not retried on 429, ambiguous acceptance, timeout or transport failure. A definite 401 rejection may be retried once after refresh.

The pacing interval is an application safeguard, not a verified claim of Schwab's current quota. A live accepted order's numeric identifier is extracted from its Location header; a missing identifier is an uncertain outcome requiring reconciliation through Schwab before resubmission.

Account hashes are resolved from the authorized account-number mapping. There is no raw-number fallback for order URLs, and ambiguous multi-account consent requires account selection. Balances come from that selected hash.

The pre-existing adapter supports basic equity market/limit submission and cancellation. Its unmapped live order-history, position and fill methods now fail explicitly instead of reporting misleading empty results. Position/fill capabilities are advertised as unavailable. Their production mappings, replacement orders, derivatives, streaming and execution reconciliation remain separate broker work; this OAuth change does **not** make those APIs complete. Local paper execution continues through the existing local simulator.

## Validation

`SchwabOAuthTest` uses mocked HTTP and temporary encrypted files, without real credentials. It covers authorization URL encoding, random single-use states, malformed/denied callbacks, HTTPS constraints, token parsing and expiry, refresh coalescing/rotation, cancellation, reauthorization generations, encrypted persistence/corruption, 401/403 handling and bounded 429 behavior. The ordinary Maven suite also runs. Live browser trust, consent, current account permissions and trading behavior must be verified with an approved Schwab app before calling this integration production-ready.

The HTTPS integration test generates a temporary test-only PKCS12 certificate using the JDK `keytool`, trusts that certificate in its test client, and exercises the actual loopback callback handler, browser response and replay rejection. This test does not change system/browser trust or contact Schwab. Other tests verify exact authorization-code form encoding, owner-restricted encrypted round trips, log redaction, cancellation of individual refresh waiters and numeric order identifiers.

Validation on Java 27, October 8, 2026:

- Clean test build: 678 tests, zero failures/errors, one existing skipped test (22 Schwab tests at that stage).
- Final clean package build: 681 tests, zero failures/errors, one existing skipped test; all 25 Schwab tests passed.
- Build logs: `output/schwab-clean-test.log` and `output/schwab-clean-package.log`.
- Runtime artifact: `output/schwab-target/investpro-1.0.0-SNAPSHOT.jar` with its companion `lib/` directory. Verified new Schwab authentication and connection-panel classes are present; the obsolete token service is absent. Launcher and dependency classpath manifest entries are present.
- The Maven wrapper ran `clean test` and `clean package` with a temporary copy of the root POM overriding only the build output directory, because the ordinary `target` directory was locked. The temporary POM was removed; the repository's Maven configuration was unchanged.
- Existing Java 27/Unsafe warnings remain. The configured SpotBugs version cannot read Java 27 class files, so the repository already skips that check; these results do not include a SpotBugs security review.

## Changed files

Created under `src/main/java/org/investpro/exchange/schwab/`:

- `SchwabOAuthClient.java`: authorization URL and code/refresh grants.
- `SchwabTokenManager.java`: expiry, single-flight refresh, session generations and reauthorization state.
- `SchwabTokenState.java`: absolute timestamps and redacted token representation.
- `SchwabTokenStore.java`: storage abstraction.
- `SchwabEncryptedTokenStore.java`: encrypted, owner-restricted atomic persistence.
- `SchwabOAuthCallbackServer.java`: short-lived HTTPS state/code validation.
- `SchwabAuthorizationFlow.java`: asynchronous callback/browser/code-exchange lifecycle.
- `SchwabAuthenticationException.java` and `SchwabApiException.java`: safe typed failures.

Also created `src/main/java/org/investpro/ui/panels/SchwabConnectionPanel.java`, `src/test/java/org/investpro/exchange/schwab/SchwabOAuthTest.java`, and this guide.

Modified `Schwab.java`, `SchwabApiClient.java`, `SchwabApiConfig.java`, `src/main/java/org/investpro/config/ProductionStartupValidator.java`, `src/main/java/org/investpro/ui/OnboardingDesk.java`, and `src/main/java/org/investpro/ui/TradingDesk.java`. Removed `SchwabOAuthTokenService.java` after migrating its callers. Other broker implementations, dependencies, Maven configuration and existing user credentials were not changed for this integration.
