package org.investpro.utils;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.coinbase.CoinbaseJwtSigner;

/**
 * Diagnostic tool to validate Coinbase API credentials before authentication.
 * Run this before trying to connect to Coinbase.
 */
@Slf4j
public class CoinbaseCredentialDiagnostic {

    public static void main(String[] args) {
        String keyName = System.getenv("COINBASE_KEY_NAME");
        String privateKey = System.getenv("COINBASE_PRIVATE_KEY");

        log.info("========== COINBASE CREDENTIAL DIAGNOSTIC ==========");
        validateCredentials(keyName, privateKey);
    }

    public static void validateCredentials(String keyName, String privateKey) {
        log.info("\n1. Checking API Key Name Format...");
        validateKeyNameFormat(keyName);

        log.info("\n2. Checking Private Key Format...");
        validatePrivateKeyFormat(privateKey);

        log.info("\n3. Checking JWT Generation...");
        validateJwtGeneration(keyName, privateKey);

        log.info("\n========== DIAGNOSTIC COMPLETE ==========");
        log.info("If all checks pass but you still get 401:");
        log.info("  - Log into Coinbase Advanced Trade dashboard");
        log.info("  - Go to API → API Keys");
        log.info("  - Verify the key name matches: {}", keyName);
        log.info("  - Verify the key is ENABLED (not disabled)");
        log.info("  - Verify it has 'View accounts' permission");
        log.info("  - If still failing, REGENERATE the API key pair");
    }

    private static void validateKeyNameFormat(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            log.error("❌ API Key Name is NULL or EMPTY");
            log.error("   Set COINBASE_KEY_NAME environment variable");
            return;
        }

        log.info("✓ API Key Name provided: {}", maskSensitive(keyName));

        if (!keyName.contains("organizations/") || !keyName.contains("/apiKeys/")) {
            log.warn("⚠ API Key Name format looks suspicious");
            log.warn("   Expected format: organizations/{{org_id}}/apiKeys/{{key_id}}");
            log.warn("   Got: {}", maskSensitive(keyName));
            return;
        }

        log.info("✓ API Key Name format is correct");
    }

    private static void validatePrivateKeyFormat(String privateKey) {
        if (privateKey == null || privateKey.isBlank()) {
            log.error("❌ Private Key is NULL or EMPTY");
            log.error("   Set COINBASE_PRIVATE_KEY environment variable");
            return;
        }

        log.info("✓ Private Key provided: {} characters", privateKey.length());

        if (!privateKey.contains("BEGIN") || !privateKey.contains("PRIVATE KEY")) {
            log.error("❌ Private Key does NOT contain PEM markers");
            log.error("   Expected to find: BEGIN ... PRIVATE KEY");
            log.error("   Got: {}", privateKey.substring(0, Math.min(50, privateKey.length())));
            return;
        }

        log.info("✓ Private Key contains PEM markers");

        if (!privateKey.contains("-----BEGIN EC PRIVATE KEY-----") &&
                !privateKey.contains("-----BEGIN PRIVATE KEY-----")) {
            log.warn("⚠ Private Key format not recognized");
            log.warn("   Expected: -----BEGIN EC PRIVATE KEY----- or -----BEGIN PRIVATE KEY-----");
            return;
        }

        log.info("✓ Private Key is in EC format");

        if (!privateKey.contains("-----END") || !privateKey.contains("PRIVATE KEY-----")) {
            log.error("❌ Private Key is missing END marker");
            log.error("   Expected: -----END ... PRIVATE KEY-----");
            return;
        }

        log.info("✓ Private Key has proper end marker");
    }

    private static void validateJwtGeneration(String keyName, String privateKey) {
        if (keyName == null || keyName.isBlank() || privateKey == null || privateKey.isBlank()) {
            log.warn("⚠ Skipping JWT validation - credentials are missing");
            return;
        }

        try {
            CoinbaseJwtSigner signer = new CoinbaseJwtSigner(keyName, privateKey);

            String restJwt = signer.buildRestJwt("GET", "/api/v3/brokerage/accounts");
            log.info("✓ REST JWT generated successfully: {} characters", restJwt.length());

            String wsJwt = signer.buildWebSocketJwt();
            log.info("✓ WebSocket JWT generated successfully: {} characters", wsJwt.length());

            log.info("✓ JWT generation works - credentials look valid");
        } catch (IllegalArgumentException e) {
            log.error("❌ JWT generation failed: {}", e.getMessage());
            log.error("   This likely means the private key format is invalid");
            log.error("   Regenerate the API key pair in Coinbase dashboard");
        } catch (Exception e) {
            log.error("❌ Unexpected error during JWT generation: {}", e.getMessage());
            log.error("   {}", e.getClass().getSimpleName());
        }
    }

    private static String maskSensitive(String value) {
        if (value == null || value.length() < 10) {
            return value;
        }
        String start = value.substring(0, 10);
        String end = value.substring(Math.max(0, value.length() - 5));
        return start + "..." + end;
    }
}
