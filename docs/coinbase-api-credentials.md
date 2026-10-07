# Connect Coinbase with an API key and secret

Enter the key name or key ID as **API Key**, and the private key from the same
Coinbase export as **API Secret**. Both ECDSA P-256 PEM keys and Ed25519 secrets
(Base64 encoding of 32 or 64 bytes) are supported. A complete JSON export with
`name` and `privateKey` can be pasted into either field.

Environment configuration accepts either pair of names:

```dotenv
COINBASE_API_KEY=your-key-name-or-id
COINBASE_API_SECRET=your-secret
```

`COINBASE_KEY_NAME` and `COINBASE_PRIVATE_KEY` remain supported aliases and take
precedence when both sets are present. Avoid keeping different key pairs in
these two sets of variables. PEM secrets may contain escaped `\n`, real
newlines, wrapping quotes, or flattened whitespace; the signer normalizes them.

The connection check signs a read-only `GET /api/v3/brokerage/accounts` request.
Only a successful account API response confirms authentication. Bot simulation
does not bypass this check. HTTP 401 indicates rejected authentication; HTTP 403
indicates missing account/portfolio permissions. View permission is required
to validate access; this check never submits an order.

Coinbase's official SDK reference:
https://github.com/coinbase/coinbase-advanced-py/blob/master/coinbase/jwt_generator.py
