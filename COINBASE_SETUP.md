# Coinbase Secure Credential Setup for InvestPro

This guide explains how to securely configure your Coinbase Advanced Trade API credentials for InvestPro.

## ⚠️ Security WARNING

**NEVER hardcode your private keys or API credentials in source code!** Hardcoded credentials are:
- Easily exposed in git repositories
- Visible in version control history (even if deleted later)
- A security liability if accidentally committed
- Violate best practices for API key management

Instead, use **environment variables** for secure credential management.

---

## Setup Steps

### Step 1: Get Your Coinbase Credentials

1. Go to [Coinbase Settings → API](https://coinbase.com/settings/api)
2. Click **"Create Key"** and select **"Advanced Trade API"**
3. Copy the following information:
   - **Key Name**: Format is `organizations/{org_id}/apiKeys/{key_id}`
   - **EC Private Key**: The PEM-formatted private key (starts with `-----BEGIN EC PRIVATE KEY-----`)

**Example credential format you'll receive:**
```
Key Name: organizations/a8dd6d6f-375a-4f4b-b0b0-75f829b998eb/apiKeys/efd80950-544c-4051-9911-b41b8d32bc67

Private Key:
-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIOtcCOGlCU6sv8fQsUkxliRPkmtZIzjKq7VETXKpukW5oAoGCCqGSM49
AwEHoUQDQgAEpIxDQbb9Raa2N0MFzUBho/sEuU6C1GwA0qP58/5t9G24iu31q+K9
4VF+Hq1v4opkoJROToqMqyu9UIZHsYl8pg==
-----END EC PRIVATE KEY-----
```

### Step 2: Set Environment Variables

**Option A: Using the Setup Script (Recommended for Windows)**

```powershell
# Run the setup script in PowerShell
.\setup-coinbase-credentials.ps1

# Follow the interactive prompts
# Script will:
# - Ask for your credentials
# - Set environment variables for this session
# - Optionally save to .env file
```

**Option B: Create .env File Manually**

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and add your credentials:
   ```
   COINBASE_KEY_NAME=organizations/a8dd6d6f-375a-4f4b-b0b0-75f829b998eb/apiKeys/efd80950-544c-4051-9911-b41b8d32bc67
   COINBASE_PRIVATE_KEY=-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIOtcCOGlCU6sv8fQsUkxliRPkmtZIzjKq7VETXKpukW5oAoGCCqGSM49\nAwEHoUQDQgAEpIxDQbb9Raa2N0MFzUBho/sEuU6C1GwA0qP58/5t9G24iu31q+K9\n4VF+Hq1v4opkoJROToqMqyu9UIZHsYl8pg==\n-----END EC PRIVATE KEY-----
   ```

3. **Add `.env` to `.gitignore`** (never commit credentials):
   ```bash
   echo ".env" >> .gitignore
   ```

**Option C: Set System Environment Variables (Persistent)**

Windows (Persistent across sessions):
```powershell
# Open "Edit the system environment variables"
# Or use PowerShell as Administrator:
[Environment]::SetEnvironmentVariable("COINBASE_KEY_NAME", "organizations/...", "User")
[Environment]::SetEnvironmentVariable("COINBASE_PRIVATE_KEY", "-----BEGIN EC PRIVATE KEY-----\n...", "User")

# Restart your IDE/terminal for changes to take effect
```

### Step 3: Load Environment Variables

**For PowerShell Sessions:**

```powershell
# Load from .env file for current session only:
Get-Content .env | ForEach-Object { 
    if ($_ -match '^([^=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

# Or load specific variables:
$env:COINBASE_KEY_NAME = Get-Content .env | Select-String 'COINBASE_KEY_NAME' | ForEach-Object { $_ -split '=' | Select-Object -Last 1 }
$env:COINBASE_PRIVATE_KEY = Get-Content .env | Select-String 'COINBASE_PRIVATE_KEY' | ForEach-Object { $_ -split '=' | Select-Object -Last 1 }
```

**For Bash/Linux/MacOS:**

```bash
# Load from .env file:
export $(cat .env | grep -v '#' | xargs)

# Or source directly:
source .env
```

### Step 4: Verify Configuration

```powershell
# Check if environment variables are set:
$env:COINBASE_KEY_NAME
$env:COINBASE_PRIVATE_KEY

# Or use InvestPro's credential checker:
# (After starting the app, it logs credential status)
```

---

## How InvestPro Uses Your Credentials

InvestPro Coinbase integration:

1. **Loads credentials in this priority order:**
   - Environment variables (`COINBASE_KEY_NAME`, `COINBASE_PRIVATE_KEY`)
   - UI-provided credentials (from OnboardingView)
   - Falls back to Java Preferences (local storage)

2. **Creates JWT tokens** using EC private key for Coinbase Advanced Trade API
   - Each request gets a new JWT signed with your private key
   - Private key never leaves your machine

3. **Logs credential source** to help debug connection issues:
   ```
   Coinbase: Using credentials from environment variables (COINBASE_KEY_NAME, COINBASE_PRIVATE_KEY)
   Coinbase: Successfully created Coinbase JWT signer
   ```

---

## Troubleshooting

### "Cannot login to Coinbase"

1. **Verify credentials are set:**
   ```powershell
   # Check environment variables exist and have values
   if ([Environment]::GetEnvironmentVariable("COINBASE_KEY_NAME")) { "✓ Key set" } else { "✗ Key NOT set" }
   if ([Environment]::GetEnvironmentVariable("COINBASE_PRIVATE_KEY")) { "✓ Private key set" } else { "✗ Private key NOT set" }
   ```

2. **Check logs for errors:**
   - Open InvestPro debug console or log file
   - Look for `Coinbase:` entries indicating credential loading status
   - Look for `JWT signer` errors

3. **Verify credential format:**
   - Key Name must start with `organizations/`
   - Private Key must be valid EC PEM format (check for `BEGIN EC PRIVATE KEY`)
   - No extra whitespace or line breaks in environment variables

4. **Test with sample code:**
   ```java
   // Quick test in SmartBotUsageExamples.java
   String keyName = System.getenv("COINBASE_KEY_NAME");
   String privateKey = System.getenv("COINBASE_PRIVATE_KEY");
   
   if (keyName != null && privateKey != null) {
       CoinbaseJwtSigner signer = new CoinbaseJwtSigner(keyName, privateKey);
       String jwt = signer.buildWebSocketJwt();
       System.out.println("JWT created: " + jwt.length() + " chars");
   }
   ```

### Private Key Format Issues

Make sure newlines are properly escaped in .env:
```
# ✗ WRONG - literal newlines break the format:
COINBASE_PRIVATE_KEY=-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIOtcCOGlCU6...

# ✓ CORRECT - use \n for newlines:
COINBASE_PRIVATE_KEY=-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIOtcCOGlCU6...\n-----END EC PRIVATE KEY-----
```

### "Credentials set but still failing"

1. **Restart your IDE/terminal** - environment variables are loaded at session start
2. **Check for typos** in environment variable names:
   - `COINBASE_KEY_NAME` (not `KEY_NAME`)
   - `COINBASE_PRIVATE_KEY` (not `PRIVATE_KEY`)
3. **Verify the private key is valid** using:
   ```java
   String secret = System.getenv("COINBASE_PRIVATE_KEY");
   boolean looksValid = secret != null && secret.contains("BEGIN") && secret.contains("PRIVATE KEY");
   System.out.println("Valid PEM format: " + looksValid);
   ```

---

## Security Best Practices

✅ **DO:**
- Use environment variables or secure vaults for credentials
- Add `.env` to `.gitignore`
- Rotate API keys regularly
- Use minimal permissions (read-only for backtesting)
- Monitor API key usage in Coinbase dashboard

❌ **DON'T:**
- Hardcode credentials in source files
- Commit `.env` to version control
- Share credentials in chat/email
- Use admin keys for trading bots
- Leave credentials visible in logs/error messages

---

## How Credential Provider Works

InvestPro includes `CoinbaseCredentialProvider` utility class:

```java
// Check if credentials are configured:
if (CoinbaseCredentialProvider.hasAdvancedTradeCredentials()) {
    String keyName = CoinbaseCredentialProvider.getKeyName();
    String privateKey = CoinbaseCredentialProvider.getPrivateKey();
    // Use them...
}

// Log status (safe, doesn't expose keys):
CoinbaseCredentialProvider.logCredentialStatus();
```

This ensures:
- Centralized credential management
- Safe logging (only shows length, not actual key)
- Easy to switch between authentication methods
- No hardcoded values in source code

---

## Next Steps

1. ✅ Set up environment variables (choose Option A, B, or C)
2. ✅ Verify credentials are loaded (see Troubleshooting)
3. ✅ Start InvestPro
4. ✅ Select "COINBASE" in the exchange dropdown
5. ✅ Leave API Key/Secret empty (uses environment variables)
6. ✅ Click "Connect" to test authentication

Your Coinbase Advanced Trade connection is now secure! 🔒
