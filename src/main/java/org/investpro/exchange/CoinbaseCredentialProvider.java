package org.investpro.exchange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Secure provider for Coinbase API credentials from environment variables.
 * 
 * Supports two credential formats:
 * 
 * 1. Advanced Trade API (EC Private Key):
 *    COINBASE_KEY_NAME="organizations/{org_id}/apiKeys/{key_id}"
 *    COINBASE_PRIVATE_KEY="-----BEGIN EC PRIVATE KEY-----\n...\n-----END EC PRIVATE KEY-----"
 * 
 * 2. Legacy REST API (API Key + Secret):
 *    COINBASE_API_KEY="your-api-key"
 *    COINBASE_API_SECRET="your-api-secret"
 * 
 * Store credentials in a .env file (NOT in version control):
 * .env:
 *   COINBASE_KEY_NAME=organizations/YOUR_ORG_ID/apiKeys/YOUR_KEY_ID
 *   COINBASE_PRIVATE_KEY=-----BEGIN EC PRIVATE KEY-----\nYOUR_PRIVATE_KEY\n-----END EC PRIVATE KEY-----
 * 
 * Then load from environment in your terminal before running the application:
 * Unix/Linux/MacOS: source .env
 * PowerShell: Get-Content .env | ForEach-Object { $parts = $_ -split '='; [Environment]::SetEnvironmentVariable($parts[0], $parts[1]) }
 */

public class CoinbaseCredentialProvider {
    private static final Logger logger = Logger.getLogger(CoinbaseCredentialProvider.class.getName());
    private static final Map<String, String> DOTENV_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean dotenvLoaded;

    public CoinbaseCredentialProvider() {
        logCredentialStatus();
    }

    /**
     * Get Coinbase Advanced Trade API key name (organizations/{org_id}/apiKeys/{key_id})
     * from COINBASE_KEY_NAME environment variable.
     * 
     * @return API key name or null if not configured
     */
    public static String getKeyName() {
        return getConfigValue("COINBASE_KEY_NAME");
    }

    /**
     * Get Coinbase Advanced Trade private key (EC PRIVATE KEY in PEM format)
     * from COINBASE_PRIVATE_KEY environment variable.
     * 
     * @return Private key PEM or null if not configured
     */
    public static String getPrivateKey() {
        return getConfigValue("COINBASE_PRIVATE_KEY");
    }

    /**
     * Get legacy REST API key from COINBASE_API_KEY environment variable.
     * 
     * @return API key or null if not configured
     */
    public static String getApiKey() {
        return getConfigValue("COINBASE_API_KEY");
    }

    /**
     * Get legacy REST API secret from COINBASE_API_SECRET environment variable.
     * 
     * @return API secret or null if not configured
     */
    public static String getApiSecret() {
        return getConfigValue("COINBASE_API_SECRET");
    }

    /**
     * Check if Advanced Trade API credentials are configured.
     * 
     * @return true if both COINBASE_KEY_NAME and COINBASE_PRIVATE_KEY are set
     */
    public static boolean hasAdvancedTradeCredentials() {
        String keyName = getKeyName();
        String privateKey = getPrivateKey();
        return keyName != null && !keyName.trim().isEmpty() && 
               privateKey != null && !privateKey.trim().isEmpty();
    }

    /**
     * Check if legacy REST API credentials are configured.
     * 
     * @return true if both COINBASE_API_KEY and COINBASE_API_SECRET are set
     */
    public static boolean hasLegacyCredentials() {
        String apiKey = getApiKey();
        String apiSecret = getApiSecret();
        return apiKey != null && !apiKey.trim().isEmpty() && 
               apiSecret != null && !apiSecret.trim().isEmpty();
    }

    /**
     * Log credential configuration status (safe for debugging).
     */
    public static void logCredentialStatus() {
        boolean advancedTrade = hasAdvancedTradeCredentials();
        boolean legacy = hasLegacyCredentials();
        
        logger.info("Coinbase Credential Status:");
        logger.info("  Advanced Trade (EC Key): " + (advancedTrade ? "CONFIGURED" : "NOT CONFIGURED"));
        if (advancedTrade) {
            String keyName = getKeyName();
            logger.info("    Key Name: " + (keyName != null ? "%s...".formatted(keyName.substring(0, Math.min(30, keyName.length()))) : "null"));
        }
        logger.info("  Legacy REST API: " + (legacy ? "CONFIGURED" : "NOT CONFIGURED"));
    }

    private static String getConfigValue(String name) {
        String value = System.getenv(name);

        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        loadDotEnvOnce();
        value = DOTENV_CACHE.get(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static void loadDotEnvOnce() {
        if (dotenvLoaded) {
            return;
        }

        synchronized (DOTENV_CACHE) {
            if (dotenvLoaded) {
                return;
            }

            findDotEnv().ifPresent(CoinbaseCredentialProvider::loadDotEnv);
            dotenvLoaded = true;
        }
    }

    private static java.util.Optional<Path> findDotEnv() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        for (int depth = 0; current != null && depth < 8; depth++) {
            Path candidate = current.resolve(".env");

            if (Files.isRegularFile(candidate)) {
                return java.util.Optional.of(candidate);
            }

            current = current.getParent();
        }

        return java.util.Optional.empty();
    }

    private static void loadDotEnv(Path dotenvPath) {
        try {
            for (String line : Files.readAllLines(dotenvPath)) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int equalsIndex = trimmed.indexOf('=');

                if (equalsIndex <= 0) {
                    continue;
                }

                String name = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();

                if (value.length() >= 2
                        && value.charAt(0) == value.charAt(value.length() - 1)
                        && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
                    value = value.substring(1, value.length() - 1);
                }

                DOTENV_CACHE.putIfAbsent(name, value);
            }
        } catch (IOException exception) {
            logger.warning("Unable to load .env file for Coinbase credentials: " + exception.getMessage());
        }
    }
}
