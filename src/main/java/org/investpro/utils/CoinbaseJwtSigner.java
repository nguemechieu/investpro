package org.investpro.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Coinbase Advanced Trade JWT signer.
 *
 * Coinbase REST private endpoints require:
 *
 * Authorization: Bearer <JWT>
 *
 * REST JWT format:
 * - alg: ES256
 * - kid: API key name, for example organizations/{org_id}/apiKeys/{key_id}
 * - nonce: random unique value
 * - iss: cdp
 * - sub: API key name
 * - nbf: now epoch seconds
 * - exp: now + 120 seconds
 * - uri: METHOD api.coinbase.com/path?query
 *
 * WebSocket JWT format:
 * - same signing format, but no uri claim.
 */
@Getter
@Setter
@Slf4j
public final class CoinbaseJwtSigner {
    public static final String DEFAULT_REQUEST_HOST = "api.coinbase.com";
    public static final long DEFAULT_TTL_SECONDS = 120L;

    private static final String ISSUER = "cdp";
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";

    private final String keyName;
    private final String privateKeyPem;
    private final ECPrivateKey privateKey;
    private final long ttlSeconds;

    static {
        if (Security.getProvider(BOUNCY_CASTLE_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public CoinbaseJwtSigner(String keyName, String privateKeyPem) {
        this(keyName, privateKeyPem, DEFAULT_TTL_SECONDS);
    }

    public CoinbaseJwtSigner(String keyName, String privateKeyPem, long ttlSeconds) {
        this.keyName = normalizeKeyName(keyName);
        this.privateKeyPem = normalizePem(privateKeyPem);
        this.privateKey = loadEcPrivateKey(this.privateKeyPem);
        this.ttlSeconds = Math.max(30L, Math.min(DEFAULT_TTL_SECONDS, ttlSeconds));
    }

    /**
     * Build a REST JWT for Coinbase Advanced Trade private endpoints.
     *
     * Example:
     * buildRestJwt("GET", "/api/v3/brokerage/accounts")
     *
     * The resulting uri claim becomes:
     * GET api.coinbase.com/api/v3/brokerage/accounts
     */
    public String buildRestJwt(String method, String requestPath) {
        return buildRestJwt(method, DEFAULT_REQUEST_HOST, requestPath);
    }

    /**
     * Build a REST JWT with explicit host.
     *
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

        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            path += "?%s".formatted(uri.getRawQuery());
        }

        return buildRestJwt(method, host, path);
    }

    /**
     * Build a WebSocket JWT.
     *
     * Coinbase WebSocket JWTs are not tied to a REST method/path, so this
     * token intentionally omits the uri claim.
     */
    public String buildWebSocketJwt() {
        return signJwt(null);
    }

    /**
     * Convenience helper for HTTP Authorization header value.
     */
    public String buildAuthorizationHeader(String method, String requestPath) {
        return "Bearer %s".formatted(buildRestJwt(method, requestPath));
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
                .notBeforeTime(java.util.Date.from(Instant.ofEpochSecond(now)))
                .expirationTime(java.util.Date.from(Instant.ofEpochSecond(now + ttlSeconds)));

        if (uriClaim != null && !uriClaim.isBlank()) {
            claimsBuilder.claim("uri", uriClaim);
        }

        JWTClaimsSet claimsSet = claimsBuilder.build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .keyID(keyName)
                .customParam("nonce", UUID.randomUUID().toString().replace("-", ""))
                .build();

        SignedJWT signedJwt = new SignedJWT(header, claimsSet);

        try {
            signedJwt.sign(new ECDSASigner(privateKey));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign Coinbase JWT.", exception);
        }
    }
    private static final Dotenv DOTENV = Dotenv.configure()
            .directory(System.getProperty("user.dir"))
            .filename(".env")
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();

    private static String env(String key) {
        String value = System.getenv(key);

        if (value == null || value.isBlank()) {
            value = DOTENV.get(key);
        }

        return value;
    }
    private static String normalizeKeyName(String keyName) {
        String value = keyName == null ? "" : keyName.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Coinbase API key name is required. Expected format: organizations/{org_id}/apiKeys/{key_id}");
        }

        return value;
    }

    private static String normalizePem(String pem) {
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

    private static String normalizeRequestPath(String requestPath) {
        String value = requestPath == null ? "" : requestPath.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException("Coinbase request path is required.");
        }

        if (value.startsWith("https://") || value.startsWith("http://")) {
            URI uri = URI.create(value);
            value = uri.getRawPath();

            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                value += "?" + uri.getRawQuery();
            }
        }

        if (!value.startsWith("/")) {
            value = "/" + value;
        }

        return value;
    }

    private static ECPrivateKey loadEcPrivateKey(String privateKeyPem) {
        try (PEMParser parser = new PEMParser(new StringReader(privateKeyPem))) {
            Object parsed = parser.readObject();

            if (parsed == null) {
                throw new IllegalArgumentException("Unable to parse Coinbase private key PEM.");
            }

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter()
                    .setProvider(BOUNCY_CASTLE_PROVIDER);

            PrivateKey privateKey;
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                privateKey = converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
            } else if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                privateKey = converter.getPrivateKey(privateKeyInfo);
            } else if (parsed instanceof PrivateKey) {
                privateKey = (PrivateKey) parsed;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported Coinbase private key format: " + parsed.getClass().getName());
            }

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
            PrivateKey ecPrivateKey = keyFactory.generatePrivate(keySpec);

            if (!(ecPrivateKey instanceof ECPrivateKey)) {
                throw new IllegalArgumentException("Coinbase private key is not an EC private key.");
            }

            return (ECPrivateKey) ecPrivateKey;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Coinbase private key PEM.", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to load Coinbase EC private key.", exception);
        }
    }

    /**
     * Small CLI test helper.
     *
     * Environment variables:
     * COINBASE_KEY_NAME="organizations/{org_id}/apiKeys/{key_id}"
     * COINBASE_PRIVATE_KEY="-----BEGIN EC PRIVATE KEY-----\n...\n-----END EC
     * PRIVATE KEY-----"
     *
     * Example:
     * java org.investpro.exchange.CoinbaseJwtSigner GET /api/v3/brokerage/accounts
     */
    static void main(String @NotNull [] args) {
        String keyName = env("COINBASE_KEY_NAME");
        String privateKey = env("COINBASE_PRIVATE_KEY");

        String method = args.length > 0 ? args[0] : "GET";
        String path = args.length > 1 ? args[1] : "/api/v3/brokerage/accounts";

        CoinbaseJwtSigner signer = new CoinbaseJwtSigner(keyName, privateKey);
        System.out.println(signer.buildRestJwt(method, path));
    }
}
