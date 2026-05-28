package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Normalizes Coinbase Advanced Trade credentials and generates Coinbase JWT tokens.
 *
 * Supports:
 * - Coinbase Advanced Trade key names: organizations/.../apiKeys/...
 * - Newer UUID-style Coinbase key ids
 * - Full Coinbase JSON payloads
 * - EC PRIVATE KEY PEM format
 * - PRIVATE KEY PKCS8 PEM format
 *
 * This class intentionally avoids external JWT dependencies.
 */
@Getter
@Setter
public final class CoinbaseAuthProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern ADVANCED_KEY_NAME_RE =
            Pattern.compile("^organizations/[^/\\s]+/apiKeys/[^/\\s]+$");

    private static final Pattern UUID_KEY_ID_RE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern PEM_HEADER_RE = Pattern.compile("-----BEGIN [A-Z ]+-----");
    private static final Pattern PEM_FOOTER_RE = Pattern.compile("-----END [A-Z ]+-----");
    private static final Pattern BASE64_BODY_RE = Pattern.compile("^[A-Za-z0-9+/=]+$");

    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private static final String COINBASE_ISSUER = "cdp";
    private static final long DEFAULT_JWT_TTL_SECONDS = 120L;

    private static final String[] API_KEY_FIELDS = {
            "apiKeyName", "api_key_name", "apiKeyId", "api_key_id", "keyId", "key_id",
            "apikey", "api_key", "name", "key_name", "key", "id"
    };

    private static final String[] SECRET_FIELDS = {
            "privateKey", "private_key", "privatePem", "private_pem", "secret"
    };

    private final @Nullable String apiKeyName;
    private final @Nullable String apiSecret;

    /**
     * Creates a Coinbase auth provider.
     *
     * @param apiKeyName Coinbase key name/id, or a full Coinbase JSON payload.
     * @param apiSecret  Coinbase private key, or a full Coinbase JSON payload.
     */
    public CoinbaseAuthProvider(String apiKeyName, String apiSecret) {
        Credentials credentials = normalize(apiKeyName, apiSecret, null);
        this.apiKeyName = credentials.apiKey();
        this.apiSecret = credentials.secret();
    }

    /**
     * Creates a Coinbase auth provider using an optional auxiliary input.
     *
     * Useful when the UI has a third field where users may paste the full Coinbase JSON.
     */
    public CoinbaseAuthProvider(String apiKeyName, String apiSecret, String auxiliaryInput) {
        Credentials credentials = normalize(apiKeyName, apiSecret, auxiliaryInput);
        this.apiKeyName = credentials.apiKey();
        this.apiSecret = credentials.secret();
    }

    public @Nullable String apiKeyName() {
        return apiKeyName;
    }

    public @Nullable String apiSecret() {
        return apiSecret;
    }

    public @Nullable String maskedKeyId() {
        return maskedKeyId(apiKeyName);
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

        try {
            parseEcPrivateKey(credentials.secret());
        } catch (Exception exception) {
            return "Coinbase private key could not be parsed as an EC private key. Paste the complete Coinbase privateKey value.";
        }

        return null;
    }

    public static boolean looksLikeApiKey(String value) {
        String text = stripWrappedQuotes(value);

        return !text.isBlank()
                && (
                ADVANCED_KEY_NAME_RE.matcher(text).matches()
                        || UUID_KEY_ID_RE.matcher(text).matches()
        );
    }

    public static @Nullable String maskedKeyId(String apiKey) {
        String text = stripWrappedQuotes(apiKey);

        if (text.isBlank()) {
            return null;
        }

        if (text.length() <= 14) {
            return text;
        }

        return "%s...%s".formatted(text.substring(0, 8), text.substring(text.length() - 6));
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
            normalized = normalized
                    .replace("\\r\\n", "\n")
                    .replace("\\n", "\n");
        }

        normalized = normalized
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

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

            return "%s\n%s\n%s\n".formatted(header, middle, footer);
        }

        String condensed = normalized.replaceAll("\\s+", "");

        if (looksLikePrivateKeyBody(condensed)) {
            return "-----BEGIN EC PRIVATE KEY-----\n%s\n-----END EC PRIVATE KEY-----\n".formatted(condensed);
        }

        return normalized;
    }

    /**
     * Returns true only when the normalized credentials look usable.
     */
    public boolean isValid() {
        return validationError(apiKeyName, apiSecret, null) == null;
    }

    /**
     * Generates a Coinbase WebSocket JWT.
     *
     * WebSocket tokens usually do not require a request URI claim.
     */
    public String generateWebSocketToken() {
        return generateJwt(null);
    }

    /**
     * Generates a Coinbase REST JWT with a URI claim.
     *
     * Example:
     * generateRestToken("GET", "api.coinbase.com", "/api/v3/brokerage/accounts")
     */
    public String generateRestToken(String method, String host, String path) {
        String cleanMethod = String.valueOf(method).trim().toUpperCase(Locale.ROOT);
        String cleanHost = String.valueOf(host).trim();
        String cleanPath = String.valueOf(path).trim();

        if (cleanMethod.isBlank()) {
            throw new IllegalArgumentException("Coinbase REST JWT method is required.");
        }

        if (cleanHost.isBlank()) {
            throw new IllegalArgumentException("Coinbase REST JWT host is required.");
        }

        if (cleanPath.isBlank()) {
            cleanPath = "/";
        }

        String uri = cleanMethod + " " + cleanHost + cleanPath;
        return generateJwt(uri);
    }

    private String generateJwt(@Nullable String uri) {
        String error = validationError(apiKeyName, apiSecret, null);

        if (error != null) {
            throw new IllegalStateException(error);
        }

        try {
            PrivateKey privateKey = parseEcPrivateKey(apiSecret);

            long now = Instant.now().getEpochSecond();
            long exp = now + DEFAULT_JWT_TTL_SECONDS;

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "ES256");
            header.put("typ", "JWT");
            header.put("kid", apiKeyName);
            header.put("nonce", UUID.randomUUID().toString());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", COINBASE_ISSUER);
            payload.put("sub", apiKeyName);
            payload.put("nbf", now);
            payload.put("exp", exp);

            if (uri != null && !uri.isBlank()) {
                payload.put("uri", uri);
            }

            String encodedHeader = base64Url(toJson(header).getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64Url(toJson(payload).getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));

            byte[] derSignature = signature.sign();
            byte[] joseSignature = derToJoseSignature(derSignature, 64);

            return signingInput + "." + base64Url(joseSignature);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate Coinbase JWT: " + exception.getMessage(), exception);
        }
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
                    && (
                    text.contains("-----BEGIN")
                            || text.contains("\\n")
                            || text.contains("\n")
                            || looksLikePrivateKeyBody(text)
            )) {
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

        if (
                text.length() >= 2
                        && text.charAt(0) == text.charAt(text.length() - 1)
                        && (text.charAt(0) == '"' || text.charAt(0) == '\'')
        ) {
            return text.substring(1, text.length() - 1).trim();
        }

        return text;
    }

    private static @NotNull PrivateKey parseEcPrivateKey(String pem) {
        String normalized = normalizeSecret(pem);

        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Private key is empty.");
        }

        byte[] der = pemToDer(normalized);

        Exception pkcs8Failure ;

        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            pkcs8Failure = exception;
        }

        try {
            BigInteger privateScalar = parseSec1PrivateScalar(der);
            ECParameterSpec ecSpec = secp256r1Spec();

            ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateScalar, ecSpec);
            return KeyFactory.getInstance("EC").generatePrivate(privateKeySpec);
        } catch (Exception sec1Failure) {
            throw new IllegalArgumentException(
                    "Unsupported EC private key format. PKCS8 error: "
                            + pkcs8Failure.getMessage()
                            + "; SEC1 error: "
                            + sec1Failure.getMessage(),
                    sec1Failure
            );
        }
    }

    private static byte[] pemToDer(String pem) {
        String cleaned = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");

        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("PEM body is empty.");
        }

        return Base64.getDecoder().decode(cleaned);
    }

    /**
     * Parses the private scalar from SEC1 EC PRIVATE KEY:
     *
     * ECPrivateKey ::= SEQUENCE {
     *   version        INTEGER,
     *   privateKey     OCTET STRING,
     *   parameters [0] ECParameters OPTIONAL,
     *   publicKey  [1] BIT STRING OPTIONAL
     * }
     */
    private static BigInteger parseSec1PrivateScalar(byte[] der) {
        Asn1Reader reader = new Asn1Reader(der);

        reader.expectTag(0x30);
        int sequenceLength = reader.readLength();
        int sequenceEnd = reader.position() + sequenceLength;

        reader.expectTag(0x02);
        int versionLength = reader.readLength();
        reader.skip(versionLength);

        reader.expectTag(0x04);
        int privateKeyLength = reader.readLength();
        byte[] privateKeyBytes = reader.readBytes(privateKeyLength);

        if (reader.position() > sequenceEnd) {
            throw new IllegalArgumentException("Invalid SEC1 private key sequence length.");
        }

        return new BigInteger(1, privateKeyBytes);
    }

    private static ECParameterSpec secp256r1Spec() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] derToJoseSignature(byte[] derSignature, int outputLength) {
        Asn1Reader reader = new Asn1Reader(derSignature);

        reader.expectTag(0x30);
        int sequenceLength = reader.readLength();
        int sequenceEnd = reader.position() + sequenceLength;

        reader.expectTag(0x02);
        byte[] r = reader.readBytes(reader.readLength());

        reader.expectTag(0x02);
        byte[] s = reader.readBytes(reader.readLength());

        if (reader.position() != sequenceEnd) {
            throw new IllegalArgumentException("Invalid DER ECDSA signature.");
        }

        int partLength = outputLength / 2;

        byte[] jose = new byte[outputLength];
        copyUnsignedInteger(r, jose, 0, partLength);
        copyUnsignedInteger(s, jose, partLength, partLength);

        return jose;
    }

    private static void copyUnsignedInteger(byte @NotNull [] source, byte[] destination, int destinationOffset, int length) {
        int sourceOffset = 0;

        while (sourceOffset < source.length - 1 && source[sourceOffset] == 0) {
            sourceOffset++;
        }

        int sourceLength = source.length - sourceOffset;

        if (sourceLength > length) {
            throw new IllegalArgumentException("ECDSA integer is larger than expected.");
        }

        int padding = length - sourceLength;

        System.arraycopy(source, sourceOffset, destination, destinationOffset + padding, sourceLength);
    }

    private static String base64Url(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');

        boolean first = true;

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }

            first = false;

            builder
                    .append('"')
                    .append(jsonEscape(entry.getKey()))
                    .append('"')
                    .append(':');

            Object value = entry.getValue();

            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder
                        .append('"')
                        .append(jsonEscape(String.valueOf(value)))
                        .append('"');
            }
        }

        builder.append('}');
        return builder.toString();
    }

    private static String jsonEscape(String value) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }

        return builder.toString();
    }

    private static final class Asn1Reader {

        private final byte[] data;
        private int position;

        private Asn1Reader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
            this.position = 0;
        }

        private int position() {
            return position;
        }

        private void expectTag(int expectedTag) {
            int actualTag = readByte();

            if (actualTag != expectedTag) {
                throw new IllegalArgumentException(
                        "Unexpected ASN.1 tag. Expected 0x"
                                + Integer.toHexString(expectedTag)
                                + " but got 0x"
                                + Integer.toHexString(actualTag)
                );
            }
        }

        private int readLength() {
            int first = readByte();

            if ((first & 0x80) == 0) {
                return first;
            }

            int byteCount = first & 0x7F;

            if (byteCount == 0 || byteCount > 4) {
                throw new IllegalArgumentException("Unsupported ASN.1 length encoding.");
            }

            int length = 0;

            for (int i = 0; i < byteCount; i++) {
                length = (length << 8) | readByte();
            }

            if (length < 0 || position + length > data.length) {
                throw new IllegalArgumentException("Invalid ASN.1 length.");
            }

            return length;
        }

        private byte[] readBytes(int length) {
            if (length < 0 || position + length > data.length) {
                throw new IllegalArgumentException("ASN.1 read exceeds input length.");
            }

            byte[] output = new byte[length];

            System.arraycopy(data, position, output, 0, length);
            position += length;

            return output;
        }

        private int readByte() {
            if (position >= data.length) {
                throw new IllegalArgumentException("Unexpected end of ASN.1 input.");
            }

            return data[position++] & 0xFF;
        }

        private void skip(int length) {
            if (length < 0 || position + length > data.length) {
                throw new IllegalArgumentException("ASN.1 skip exceeds input length.");
            }

            position += length;
        }
    }

    public record Credentials(String apiKey, String secret) {
    }
}
