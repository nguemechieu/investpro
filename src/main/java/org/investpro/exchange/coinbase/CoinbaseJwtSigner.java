package org.investpro.exchange.coinbase;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * Coinbase Advanced Trade JWT signer.
 * <p>
 * Coinbase REST private endpoints require:
 * <p>
 * Authorization: Bearer <JWT>
 * <p>
 * REST JWT format:
 * - alg: ES256
 * - kid: API key name, for example organizations/{org_id}/apiKeys/{key_id}
 * - nonce: random unique value
 * - iss: cdp
 * - sub: API key name
 * - nbf: now epoch seconds
 * - exp: now + 120 seconds
 * - uri: METHOD api.coinbase.com/path (query parameters are excluded)
 * <p>
 * WebSocket JWT format:
 * - same signing format, but no uri claim.
 */

@Slf4j
public record CoinbaseJwtSigner(String keyName, String privateKeyPem, PrivateKey privateKey, long ttlSeconds) {
    public static final String DEFAULT_REQUEST_HOST = "api.coinbase.com";
    public static final long DEFAULT_TTL_SECONDS = 120L;

    private static final String ISSUER = "cdp";
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";

    static {
        if (Security.getProvider(BOUNCY_CASTLE_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public CoinbaseJwtSigner(String keyName, String privateKeyPem) {
        this(keyName, privateKeyPem, DEFAULT_TTL_SECONDS);
    }

    public CoinbaseJwtSigner(String keyName, String privateKeyPem, long ttlSeconds) {
        this(normalizeKeyName(keyName), normalizeSecret(privateKeyPem), loadPrivateKey(privateKeyPem), Math.clamp(ttlSeconds, 30L, DEFAULT_TTL_SECONDS));
    }

    /**
     * Build a REST JWT for Coinbase Advanced Trade private endpoints.
     * <p>
     * Example:
     * buildRestJwt("GET", "/api/v3/brokerage/accounts")
     * <p>
     * The resulting uri claim becomes:
     * GET api.coinbase.com/api/v3/brokerage/accounts
     */
    public String buildRestJwt(String method, String requestPath) {
        return buildRestJwt(method, DEFAULT_REQUEST_HOST, requestPath);
    }

    /**
     * Build a REST JWT with explicit host.
     * <p>
     * Example:
     * buildRestJwt("POST", "api.coinbase.com", "/api/v3/brokerage/orders")
     */
    public String buildRestJwt(String method, String requestHost, String requestPath) {
        String normalizedMethod = normalizeMethod(method);
        String normalizedHost = normalizeHost(requestHost);
        String normalizedPath = normalizeRequestPath(requestPath);

        String uri = "%s %s%s".formatted(normalizedMethod, normalizedHost, normalizedPath);

        return signJwt(uri);
    }

    /**
     * Build a REST JWT directly from a full URL
     * Example:
     * buildRestJwtForUrl("GET",
     * "<a href="https://api.coinbase.com/api/v3/brokerage/accounts">...</a>")
     */
    public String buildRestJwtForUrl(String method, String fullUrl) {
        Objects.requireNonNull(fullUrl, "fullUrl must not be null");

        URI uri = URI.create(fullUrl);
        String host = uri.getHost();
        String path = uri.getRawPath();

        return buildRestJwt(method, host, path);
    }

    /**
     * Build a WebSocket JWT.
     * <p>
     * Coinbase WebSocket JWTs are not tied to a REST method/path, so this
     * token intentionally omits the uri claim.
     */
    public String buildWebSocketJwt() {
        return signJwt(null);
    }

    /**
     * Convenience helper for HTTP Authorization header value.
     */
    public String buildAuthorizationHeader(String method, String host, String path) {
        String jwt = buildRestJwt(method, host, path);
        return "Bearer " + jwt;
    }

    /**
     * Convenience helper for HTTP Authorization header value from full URL.
     */
    public @NotNull String buildAuthorizationHeaderForUrl(String method, String fullUrl) {
        return "Bearer %s".formatted(buildRestJwtForUrl(method, fullUrl));
    }

    private String signJwt(String uriClaim) {
        long now = Instant.now().getEpochSecond();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(keyName)
                .notBeforeTime(Date.from(Instant.ofEpochSecond(now)))
                .expirationTime(Date.from(Instant.ofEpochSecond(now + ttlSeconds)));

        if (uriClaim != null && !uriClaim.isBlank()) {
            claimsBuilder.claim("uri", uriClaim);
        }

        JWTClaimsSet claimsSet = claimsBuilder.build();

        JWSHeader header = new JWSHeader.Builder(privateKey instanceof ECPrivateKey ? JWSAlgorithm.ES256 : JWSAlgorithm.EdDSA)
                .type(JOSEObjectType.JWT)
                .keyID(keyName)
                .customParam("nonce", UUID.randomUUID().toString().replace("-", ""))
                .build();

        SignedJWT signedJwt = new SignedJWT(header, claimsSet);

        try {
            if (privateKey instanceof ECPrivateKey ecKey) {
                signedJwt.sign(new ECDSASigner(ecKey));
            } else {
                signedJwt.sign(new com.nimbusds.jose.JWSSigner() {
                    private final com.nimbusds.jose.jca.JCAContext context = new com.nimbusds.jose.jca.JCAContext();
                    @Override public Set<JWSAlgorithm> supportedJWSAlgorithms() { return Set.of(JWSAlgorithm.EdDSA); }
                    @Override public com.nimbusds.jose.jca.JCAContext getJCAContext() { return context; }
                    @Override public com.nimbusds.jose.util.Base64URL sign(JWSHeader header, byte[] input) throws JOSEException {
                        try {
                            java.security.Signature signature = java.security.Signature.getInstance("Ed25519");
                            signature.initSign(privateKey);
                            signature.update(input);
                            return com.nimbusds.jose.util.Base64URL.encode(signature.sign());
                        } catch (java.security.GeneralSecurityException error) {
                            throw new JOSEException("Unable to sign Coinbase Ed25519 JWT", error);
                        }
                    }
                });
            }
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign Coinbase JWT.", exception);
        }
    }



    private static String normalizeKeyName(String keyName) {
        String value = keyName == null ? "" : keyName.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Coinbase API key name is required. Expected format: organizations/{org_id}/apiKeys/{key_id}");
        }

        return value;
    }

    public static String normalizePem(String pem) {
        String value = pem == null ? "" : pem.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException("Coinbase EC private key PEM is required.");
        }

        /*
         * Environment variables often store private keys with escaped newlines.
         * Coinbase examples show replacing "\\n" with real newlines before parsing.
         */
        value = stripWrappingQuotes(value);
        value = value
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        /*
         * Some UI/password fields and .env editors collapse PEMs into a single line:
         * -----BEGIN ...----- base64 -----END ...-----
         * PEMParser needs canonical line boundaries, so rebuild the block.
         */
        value = canonicalizePemBlock(value);

        if (!value.contains("BEGIN") || !value.contains("PRIVATE KEY")) {
            throw new IllegalArgumentException(
                    "Invalid Coinbase private key PEM. Expected -----BEGIN EC PRIVATE KEY----- or -----BEGIN PRIVATE KEY-----.");
        }

        return value;
    }

    private static String stripWrappingQuotes(String value) {
        String normalized = value == null ? "" : value.trim();
        boolean changed = true;

        while (changed && normalized.length() >= 2) {
            changed = false;
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
                changed = true;
            }
        }

        return normalized;
    }

    private static String canonicalizePemBlock(String pem) {
        String value = pem == null ? "" : pem.trim();
        String beginPrefix = "-----BEGIN ";
        String endPrefix = "-----END ";

        int beginStart = value.indexOf(beginPrefix);
        if (beginStart < 0) {
            return value;
        }

        int beginEnd = value.indexOf("-----", beginStart + beginPrefix.length());
        if (beginEnd < 0) {
            return value;
        }
        beginEnd += "-----".length();

        int endStart = value.indexOf(endPrefix, beginEnd);
        if (endStart < 0) {
            return value;
        }

        int endEnd = value.indexOf("-----", endStart + endPrefix.length());
        if (endEnd < 0) {
            return value;
        }
        endEnd += "-----".length();

        String header = value.substring(beginStart, beginEnd).trim();
        String footer = value.substring(endStart, endEnd).trim();
        String body = value.substring(beginEnd, endStart)
                .replaceAll("\\s+", "")
                .trim();

        if (body.isBlank()) {
            return value;
        }

        List<String> lines = new ArrayList<>();
        lines.add(header);
        for (int index = 0; index < body.length(); index += 64) {
            lines.add(body.substring(index, Math.min(index + 64, body.length())));
        }
        lines.add(footer);
        return String.join("\n", lines);
    }

    private static String normalizeMethod(String method) {
        String value = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);

        if (value.isBlank()) {
            throw new IllegalArgumentException("HTTP method is required.");
        }

        return value;
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host.trim();

        if (value.isBlank()) {
            return DEFAULT_REQUEST_HOST;
        }

        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }

        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0) {
            value = value.substring(0, slashIndex);
        }

        return value;
    }

    private static @NonNull String normalizeRequestPath(String requestPath) {
        String value = requestPath == null ? "" : requestPath.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException("Coinbase request path is required.");
        }

        if (value.startsWith("https://") || value.startsWith("http://")) {
            URI uri = URI.create(value);
            value = uri.getRawPath();

        }

        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) value = value.substring(0, queryIndex);
        int fragmentIndex = value.indexOf('#');
        if (fragmentIndex >= 0) value = value.substring(0, fragmentIndex);

        if (!value.startsWith("/")) {
            value = "/" + value;
        }

        return value;
    }

    private static String normalizeSecret(String input) {
        String value = stripWrappingQuotes(input);
        return value.contains("-----BEGIN ") ? normalizePem(value) : value.replaceAll("\\s+", "");
    }

    private static @NonNull PrivateKey loadPrivateKey(String input) {
        String secret = normalizeSecret(input);
        try {
            PrivateKey key;
            if (!secret.startsWith("-----BEGIN ")) {
                byte[] raw = Base64.getDecoder().decode(secret);
                if (raw.length != 32 && raw.length != 64)
                    throw new IllegalArgumentException("Ed25519 secret must decode to 32 or 64 bytes.");
                byte[] encoded = new byte[48];
                byte[] prefix = HexFormat.of().parseHex("302e020100300506032b657004220420");
                System.arraycopy(prefix, 0, encoded, 0, prefix.length);
                System.arraycopy(raw, 0, encoded, prefix.length, 32);
                key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
            } else {
                try (PEMParser parser = new PEMParser(new StringReader(secret))) {
                    Object parsed = parser.readObject();
                    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BOUNCY_CASTLE_PROVIDER);
                    key = switch (parsed) {
                        case PEMKeyPair pair -> converter.getPrivateKey(pair.getPrivateKeyInfo());
                        case PrivateKeyInfo info -> converter.getPrivateKey(info);
                        default -> throw new IllegalArgumentException("Unsupported Coinbase private key format.");
                    };
                }
                String algorithm = key instanceof ECPrivateKey ? "EC" : "Ed25519";
                key = KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(key.getEncoded()));
            }
            if (key instanceof ECPrivateKey ec) {
                java.security.AlgorithmParameters parameters = java.security.AlgorithmParameters.getInstance("EC");
                parameters.init(new java.security.spec.ECGenParameterSpec("secp256r1"));
                var expected = parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
                if (!ec.getParams().getCurve().equals(expected.getCurve()) || !ec.getParams().getOrder().equals(expected.getOrder()))
                    throw new IllegalArgumentException("Coinbase ES256 requires an ECDSA P-256 key.");
            }
            return key;
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to load Coinbase secret. Use an ECDSA P-256 PEM or Ed25519 private key.", error);
        }
    }

    @Override public String toString() { return "CoinbaseJwtSigner[redacted]"; }
    /**
     * Small CLI test helper.
     * <p>
     * Environment variables:
     * COINBASE_KEY_NAME="organizations/{org_id}/apiKeys/{key_id}"
     * COINBASE_PRIVATE_KEY="-----BEGIN EC PRIVATE KEY-----\n...\n-----END EC
     * PRIVATE KEY-----"
     * <p>
     * Example:
     * java org.investpro.exchange.CoinbaseJwtSigner GET /api/v3/brokerage/accounts
     */
    static void main(String @NotNull [] args) {
        String keyName = System.getProperty("COINBASE_KEY_NAME");
        String privateKey = System.getProperty("COINBASE_PRIVATE_KEY");

        String method = args.length > 0 ? args[0] : "GET";
        String path = args.length > 1 ? args[1] : "/api/v3/brokerage/accounts";

        CoinbaseJwtSigner signer = new CoinbaseJwtSigner(keyName, privateKey);
        signer.buildRestJwt(method, path);
        log.info("Coinbase REST JWT generated successfully; token omitted.");
    }
}
