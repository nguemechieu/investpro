package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalizes Coinbase Advanced Trade credentials the same way the Sopotek broker does.
 */
public final class CoinbaseCredentials {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern ADVANCED_KEY_NAME_RE =
            Pattern.compile("^organizations/[^/\\s]+/apiKeys/[^/\\s]+$");
    private static final Pattern UUID_KEY_ID_RE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern PEM_HEADER_RE = Pattern.compile("-----BEGIN [A-Z ]+-----");
    private static final Pattern PEM_FOOTER_RE = Pattern.compile("-----END [A-Z ]+-----");
    private static final Pattern BASE64_BODY_RE = Pattern.compile("^[A-Za-z0-9+/=]+$");

    private static final String[] API_KEY_FIELDS = {
            "apiKeyName", "api_key_name", "apiKeyId", "api_key_id", "keyId", "key_id",
            "apikey", "api_key", "name", "key_name", "key", "id"
    };
    private static final String[] SECRET_FIELDS = {
            "privateKey", "private_key", "privatePem", "private_pem", "secret"
    };

    private CoinbaseCredentials() {
    }

    public static @NotNull Credentials normalize(String apiKeyInput, String secretInput, String auxiliaryInput) {
        Map<String, String> parsed = parsePayloads(apiKeyInput, secretInput, auxiliaryInput);
        String normalizedApiKey = normalizeApiKey(parsed.getOrDefault("api_key", apiKeyInput));
        String normalizedSecret = normalizeSecret(parsed.getOrDefault("secret", secretInput));

        if (normalizedSecret == null) {
            normalizedSecret = normalizeSecret(auxiliaryInput);
        }
        if (normalizedApiKey == null) {
            normalizedApiKey = normalizeApiKey(auxiliaryInput);
        }

        return new Credentials(normalizedApiKey, normalizedSecret);
    }

    public static @Nullable String validationError(String apiKeyInput, String secretInput, String auxiliaryInput) {
        Credentials credentials = normalize(apiKeyInput, secretInput, auxiliaryInput);

        if (credentials.apiKey() == null || credentials.apiKey().isBlank()) {
            return "Coinbase credentials are missing the key identifier. Paste the Advanced Trade key name, the newer UUID key id, or the full Coinbase key JSON.";
        }
        if (!looksLikeApiKey(credentials.apiKey())) {
            return "Coinbase key format is not recognized. Use organizations/.../apiKeys/... or the newer UUID-style key id from Coinbase.";
        }
        if (credentials.secret() == null || credentials.secret().isBlank()) {
            return "Coinbase private key is missing. Paste the privateKey value or the full Coinbase key JSON.";
        }

        java.util.regex.Matcher headerMatch = PEM_HEADER_RE.matcher(credentials.secret());
        java.util.regex.Matcher footerMatch = PEM_FOOTER_RE.matcher(credentials.secret());
        if (!headerMatch.find() || !footerMatch.find() || headerMatch.start() >= footerMatch.start()) {
            return "Coinbase private key is malformed. Paste the full privateKey value or the full Coinbase key JSON.";
        }

        String body = credentials.secret().substring(headerMatch.end(), footerMatch.start());
        String condensed = body.replaceAll("\\s+", "");
        if (condensed.isBlank()) {
            return "Coinbase private key is missing its encoded body. Paste the full privateKey value from Coinbase.";
        }
        if (condensed.length() < 32) {
            return "Coinbase private key looks truncated. Paste the complete privateKey value, not a shortened snippet.";
        }

        return null;
    }

    public static boolean looksLikeApiKey(String value) {
        String text = stripWrappedQuotes(value);
        return !text.isBlank()
                && (ADVANCED_KEY_NAME_RE.matcher(text).matches() || UUID_KEY_ID_RE.matcher(text).matches());
    }

    public static @Nullable String maskedKeyId(String apiKey) {
        String text = stripWrappedQuotes(apiKey);
        if (text.isBlank()) {
            return null;
        }
        if (text.length() <= 14) {
            return text;
        }
        return text.substring(0, 8) + "..." + text.substring(text.length() - 6);
    }

    public static @Nullable String normalizeApiKey(String value) {
        String normalized = stripWrappedQuotes(value);
        return normalized.isBlank() ? null : normalized;
    }

    public static @Nullable String normalizeSecret(String value) {
        String normalized = stripWrappedQuotes(value);
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.contains("\\n")) {
            normalized = normalized.replace("\\r\\n", "\n").replace("\\n", "\n");
        }
        normalized = normalized.replace("\r\n", "\n").replace("\r", "\n").trim();

        java.util.regex.Matcher headerMatch = PEM_HEADER_RE.matcher(normalized);
        java.util.regex.Matcher footerMatch = PEM_FOOTER_RE.matcher(normalized);
        if (headerMatch.find() && footerMatch.find() && headerMatch.start() < footerMatch.start()) {
            String header = headerMatch.group();
            String footer = footerMatch.group();
            String middleRaw = normalized.substring(headerMatch.end(), footerMatch.start());
            StringBuilder middle = new StringBuilder();
            for (String line : middleRaw.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    if (middle.length() > 0) {
                        middle.append('\n');
                    }
                    middle.append(trimmed);
                }
            }
            return header + "\n" + middle + "\n" + footer + "\n";
        }

        String condensed = normalized.replaceAll("\\s+", "");
        if (looksLikePrivateKeyBody(condensed)) {
            return "-----BEGIN EC PRIVATE KEY-----\n" + condensed + "\n-----END EC PRIVATE KEY-----\n";
        }

        return normalized;
    }

    private static boolean looksLikePrivateKeyBody(String value) {
        String text = stripWrappedQuotes(value);
        return !text.isBlank()
                && text.length() >= 48
                && BASE64_BODY_RE.matcher(text.replaceAll("\\s+", "")).matches();
    }

    private static @NotNull Map<String, String> parsePayloads(String... values) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String rawValue : values) {
            String text = stripWrappedQuotes(rawValue);
            if (text.isBlank()) {
                continue;
            }

            JsonNode payload = tryParseJson(text);
            if (payload != null && payload.isObject()) {
                Map<String, String> flattened = new LinkedHashMap<>();
                flattenJson(payload, flattened);
                resolveField(flattened, resolved, "api_key", API_KEY_FIELDS);
                resolveField(flattened, resolved, "secret", SECRET_FIELDS);
            }

            if (!resolved.containsKey("api_key") && looksLikeApiKey(text)) {
                resolved.put("api_key", text);
            }
            if (!resolved.containsKey("secret")
                    && (text.contains("-----BEGIN") || text.contains("\\n") || text.contains("\n")
                    || looksLikePrivateKeyBody(text))) {
                resolved.put("secret", text);
            }
        }
        return resolved;
    }

    private static void resolveField(
            Map<String, String> flattened,
            Map<String, String> resolved,
            String target,
            String[] fieldNames
    ) {
        if (resolved.containsKey(target)) {
            return;
        }
        for (String fieldName : fieldNames) {
            String candidate = flattened.get(normalizeFieldName(fieldName));
            if (candidate != null && !candidate.isBlank()) {
                resolved.put(target, candidate);
                return;
            }
        }
    }

    private static void flattenJson(JsonNode node, Map<String, String> flattened) {
        if (node == null || node.isNull() || !node.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }

            if (value.isObject()) {
                flattenJson(value, flattened);
                continue;
            }

            if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                String normalizedName = normalizeFieldName(entry.getKey());
                String text = stripWrappedQuotes(value.asText());
                if (!normalizedName.isBlank() && !text.isBlank()) {
                    flattened.putIfAbsent(normalizedName, text);
                }
            }
        }
    }

    private static @Nullable JsonNode tryParseJson(String text) {
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static @NotNull String normalizeFieldName(String fieldName) {
        return String.valueOf(fieldName)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static @NotNull String stripWrappedQuotes(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() >= 2 && text.charAt(0) == text.charAt(text.length() - 1)
                && (text.charAt(0) == '"' || text.charAt(0) == '\'')) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    public record Credentials(String apiKey, String secret) {
    }
}
