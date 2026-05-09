# Coinbase 401 Unauthorized - Troubleshooting Guide

## The Error
```
Coinbase HTTP 401 for https://api.coinbase.com/api/v3/brokerage/accounts: Unauthorized
```

**This means your API key credentials are invalid, mismatched, or the key is disabled in Coinbase.**

## Solution Steps

### Step 1: Verify Your Credentials Are Set
Check that these environment variables are set:
```powershell
# In PowerShell, check current env vars
$env:COINBASE_KEY_NAME
$env:COINBASE_PRIVATE_KEY
```

If empty, set them:
```powershell
# Set temporarily in PowerShell
$env:COINBASE_KEY_NAME = "organizations/YOUR_ORG_ID/apiKeys/YOUR_KEY_ID"
$env:COINBASE_PRIVATE_KEY = @"
-----BEGIN EC PRIVATE KEY-----
...your private key content...
-----END EC PRIVATE KEY-----
"@
```

### Step 2: Verify API Key Format

Your **COINBASE_KEY_NAME** should follow this exact format:
```
organizations/{org_id}/apiKeys/{key_id}
```

❌ **WRONG:**
- `my-api-key`
- `coinbase_key_abc123`
- `YOUR_ORG_ID/YOUR_KEY_ID`

✅ **CORRECT:**
- `organizations/550e8400-e29b-41d4-a716-446655440000/apiKeys/abcd-1234-efgh-5678`

### Step 3: Check Coinbase Dashboard

1. Log in to https://advanced.coinbase.com
2. Navigate to **Settings → API**
3. Select your API key
4. **Verify:**
   - ✓ Key is **ENABLED** (not disabled or revoked)
   - ✓ Key name exactly matches your `COINBASE_KEY_NAME`
   - ✓ Key has **"View accounts"** permission enabled
   - ✓ Private key file was downloaded and matches `COINBASE_PRIVATE_KEY`

### Step 4: Regenerate If Needed

If the key looks disabled or you're unsure:

1. Go to **Settings → API**
2. Click **Revoke** on the old key
3. Click **Create API Key**
4. Download the private key file (save in safe location)
5. Copy the key name (format: `organizations/xxx/apiKeys/yyy`)
6. Set both env variables with the new credentials
7. Restart InvestPro

### Step 5: Run the Diagnostic

After setting credentials, compile and run the diagnostic class:

```powershell
cd c:\Users\nguem\Documents\GitHub\investpro

# Compile the project
.\mvnw.cmd clean compile

# Run just the diagnostic (no need to run full app)
# Edit Coinbase.java line 1042 temporarily to call the diagnostic, or:
# Create a simple test file that calls CoinbaseCredentialDiagnostic.validateCredentials()
```

### Step 6: Test Credentials in Isolation

Create a simple test file `TestCoinbase.java`:

```java
import org.investpro.utils.CoinbaseCredentialDiagnostic;

public class TestCoinbase {
    static void main(String[] args) {
        String keyName = System.getenv("COINBASE_KEY_NAME");
        String privateKey = System.getenv("COINBASE_PRIVATE_KEY");
        
        CoinbaseCredentialDiagnostic.validateCredentials(keyName, privateKey);
    }
}
```

Then run:
```powershell
.\mvnw.cmd compile exec:java -Dexec.mainClass="TestCoinbase"
```

## Common Issues & Fixes

### Issue: "Key name format looks suspicious"
**Cause:** API key name doesn't match the correct format
**Fix:** Copy the exact key name from Coinbase dashboard (Settings → API → your key)

### Issue: "Private Key does NOT contain PEM markers"
**Cause:** You're pasting the wrong thing into `COINBASE_PRIVATE_KEY`
**Fix:** The env var should contain the full PEM-formatted private key:
```
-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIIP...
...
...5jgLJPPu+Ew==
-----END EC PRIVATE KEY-----
```

### Issue: "Private Key is missing END marker"
**Cause:** Private key is incomplete or corrupted
**Fix:** Download a fresh private key from Coinbase dashboard

### Issue: JWT generation works but still getting 401
**Cause:** 
- API key is disabled in Coinbase dashboard
- Key permissions changed (missing "View accounts")
- IP address restricted (if you set IP restrictions)

**Fix:**
- Check Coinbase dashboard → API key is enabled
- Verify permissions include "View accounts"
- If IP restriction enabled, whitelist your current IP

## How Coinbase Auth Works

```
1. API Key Name + Private Key →
2. Generate JWT (signed with private key) →
3. Send JWT in Authorization header →
4. Coinbase verifies JWT signature →
5. ✓ Accepted OR ✗ 401 Unauthorized
```

If JWT generation works but you still get 401, the problem is on the Coinbase side (disabled key, wrong permissions, IP restriction, etc.).

## Quick Verification Checklist

- [ ] `COINBASE_KEY_NAME` set and has correct format: `organizations/.../apiKeys/...`
- [ ] `COINBASE_PRIVATE_KEY` set and starts with `-----BEGIN EC PRIVATE KEY-----`
- [ ] Logged into Coinbase Advanced Trade dashboard
- [ ] API key **ENABLED** (not disabled/revoked)
- [ ] API key has **"View accounts"** permission
- [ ] No IP restrictions set (or current IP is whitelisted)
- [ ] Private key file matches the API key created date

## Next Steps

1. Follow steps 1-4 above to ensure credentials are correct
2. Run the diagnostic tool
3. Restart InvestPro
4. If still getting 401, verify all dashboard checks in Step 3

If you're still having issues after following all steps, the API key may need to be regenerated from scratch.
