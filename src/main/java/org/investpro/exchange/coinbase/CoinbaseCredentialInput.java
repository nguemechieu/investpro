package org.investpro.exchange.coinbase;

import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.Base64;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1OctetString;

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
        key = fieldValue(key, "name");
        secret = fieldValue(secret, "privateKey");
        String json = exportJson(key);
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
        } else {
            secret = restoreEncodedPem(secret);
        }
        return new Normalized(key, secret);
    }

    private static String fieldValue(String value, String field) {
        if (!value.matches("(?s)^\"" + field + "\"\\s*:.*")) return value;
        try {
            JSONObject fragment = new JSONObject("{" + value.replaceFirst(",\\s*$", "") + "}");
            // Complete exports are processed together to keep their key name and secret paired.
            if (fragment.has("name") && fragment.has("privateKey")) return value;
            return fragment.getString(field);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Coinbase " + field + " field.");
        }
    }

    /** Shared input validation for onboarding and the trading desk; never includes key material. */
    public static String validationError(String keyInput, String secretInput) {
        try {
            Normalized value = normalize(keyInput, secretInput);
            if (value.keyName().isBlank()) return "Coinbase API key name is required.";
            if (value.privateKey().isBlank()) return "Coinbase private key is required.";
            new CoinbaseJwtSigner(value.keyName(), value.privateKey());
            return null;
        } catch (IllegalArgumentException invalid) {
            return "Invalid Coinbase input. Paste the downloaded JSON, a complete ECDSA P-256 PEM, or an Ed25519 secret.";
        }
    }

    private static String restoreEncodedPem(String value) {
        String compact = value.replaceAll("\\s+", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(compact);
            // Raw Ed25519 keys must retain their original format.
            if (bytes.length == 32 || bytes.length == 64) return compact;
            ASN1Sequence sequence = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(bytes));
            String type;
            if (sequence.size() >= 3 && sequence.getObjectAt(1) instanceof ASN1Sequence) type = "PRIVATE KEY";
            else if (sequence.size() >= 2 && sequence.getObjectAt(1) instanceof ASN1OctetString) type = "EC PRIVATE KEY";
            else return value;
            return "-----BEGIN " + type + "-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                    + "\n-----END " + type + "-----";
        } catch (Exception invalidEncoding) {
            return value;
        }
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
            if (value.matches("(?s)^\"(?:name|privateKey)\"\\s*:.*")) break;
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }
}
