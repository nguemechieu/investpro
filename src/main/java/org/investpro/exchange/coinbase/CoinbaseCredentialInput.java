package org.investpro.exchange.coinbase;

import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.Base64;

/** Normalizes pasted CDP exports without guessing missing credential material. */
public final class CoinbaseCredentialInput {
    private CoinbaseCredentialInput() {}

    public record Normalized(String keyName, String privateKey) {
        @Override
        public @NonNull String toString() {
            return "CoinbaseCredentials[redacted]";
        }
    }

    public static Normalized normalize(String keyInput, String secretInput) {
        String key = unwrap(keyInput, "COINBASE_KEY_NAME", "COINBASE_API_KEY");
        String secret = unwrap(secretInput, "COINBASE_PRIVATE_KEY", "COINBASE_API_SECRET");
        String json = exportJson(keyInput);
        if (json == null) json = exportJson(secretInput);
        if (json == null) json = exportJson(key);
        if (json == null) json = exportJson(secret);
        if (json != null) {
            try {
                JSONObject export = new JSONObject(json);
                key = export.getString("name");
                secret = export.getString("privateKey");
            } catch (RuntimeException invalidExport) {
                throw new IllegalArgumentException("Coinbase JSON must contain name and privateKey string fields.");
            }
        }
        key = unwrap(key);
        secret = unwrap(secret);
        if (secret.contains("-----BEGIN ")) {
            secret = CoinbaseJwtSigner.normalizePem(secret);
        }
        return new Normalized(key, secret);
    }

    private static String exportJson(String input) {
        String value = input == null ? "" : input.strip().replace("\uFEFF", "");
        if (value.startsWith("{")) return value;
        if (value.matches("(?s)^\"(?:name|privateKey)\"\\s*:.*")) {
            return "{" + value.replaceFirst(",\\s*$", "") + "}";
        }
        return null;
    }

    public static boolean isRawEd25519Key(String secret) {
        try {
            int length = Base64.getDecoder().decode(secret.replaceAll("\\s+", "")).length;
            return length == 32 || length == 64;
        } catch (IllegalArgumentException invalidBase64) {
            return false;
        }
    }

    private static String unwrap(String input, String... assignments) {
        String value = input == null ? "" : input.strip().replace("\uFEFF", "");
        for (String assignment : assignments) {
            if (value.matches("(?s)^(?:export\\s+)?" + assignment + "\\s*=.*")) {
                value = value.substring(value.indexOf('=') + 1).strip();
                break;
            }
        }
        while (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }
}
