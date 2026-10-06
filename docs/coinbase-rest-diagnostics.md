# Coinbase REST authentication and discovery

InvestPro loads `/api/v3/brokerage/market/products` with no Authorization header. Account-specific spot tradability and optional futures/perpetual eligibility use the authenticated `/api/v3/brokerage/products` endpoint. A private discovery 401/403 leaves public spot products available and marks the affected derivative capability `PERMISSION_REQUIRED`.

Use `Coinbase.diagnoseRestAuthentication()` on a background thread. It performs read-only requests to accounts without a query, products without a query, and products with spot/tradability query parameters, stopping at the first failure. Returned results identify the failing step; logs report PASS/FAIL without response bodies or tokens. This diagnostic performs real requests only when explicitly invoked.

REST JWT URI claims preserve the request method, host, raw path, and exact raw query string as required by this implementation. WebSocket authentication does not validate a REST URI claim. Check URI generation, read permissions, key activation/revocation, PEM loading, and derivative account/region eligibility when investigating unauthorized responses.

PEM input supports EC SEC1 and PKCS8 keys, including escaped newlines. Opaque non-PEM secrets are rejected. Prebuilt tokens require explicit `Bearer <compact JWT>` input; they remain endpoint-specific and expire, so a CDP EC key is preferred. Local token formatting checks do not prove Coinbase acceptance.

Paste the downloaded JSON export in either credential field; `name`/`privateKey` field fragments without outer braces are also accepted. A raw Base64 value decoding to 64 bytes is identified as a likely Ed25519 key and rejected with instructions to select ECDSA in the CDP key creation Advanced Settings. Adding PEM markers does not change a key's cryptographic algorithm. [Coinbase App authentication requirements](https://docs.cdp.coinbase.com/coinbase-app/authentication-authorization/api-key-authentication) require ECDSA/ES256 for Advanced Trade authentication.

Configuration and credential summaries are redacted, including short values. REST response bodies and outgoing WebSocket subscription payloads are omitted from logs to avoid reflecting sensitive credentials.

Public endpoint reference: [Coinbase List Public Products](https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/public/list-public-products).

The standalone `org.investpro.utils.CoinbaseCredentialDiagnostic` follows the same sequence as the supplied working diagnostic: public products, authenticated accounts, authenticated products, then a product query. It reads `COINBASE_KEY_NAME` and `COINBASE_PRIVATE_KEY` from Java system properties first, falling back to environment variables. Each private request receives a newly generated JWT using the app's signer. Status results distinguish local key parsing, HTTP authentication and query failures; response bodies are discarded. Invoke it explicitly from the IDE with the project runtime classpath; it is not run automatically during startup.
