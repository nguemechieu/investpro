package org.investpro.investpro.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Coinbase Advanced Trade private REST authentication helper.
 */
public final class CoinbaseExchangeAuth {

    private static final SecureRandom NONCE_RANDOM = new SecureRandom();
    private static final byte[] INTEGER_ZERO = {0x02, 0x01, 0x00};
    private static final String USER_AGENT = "InvestPro";
    private static final int TOKEN_TTL_SECONDS = 120;

    private final CoinbaseCredentials.Credentials credentials;

    public CoinbaseExchangeAuth(String apiKey, String apiSecret, String auxiliaryInput) {
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        Objects.requireNonNull(apiSecret, "apiSecret must not be null");
        Objects.requireNonNull(auxiliaryInput, "auxiliaryInput must not be null");
        this.credentials = CoinbaseCredentials.normalize(apiKey, apiSecret, auxiliaryInput);
    }

    public HttpRequest.Builder authorize(
            @NotNull HttpRequest.Builder builder,
            @NotNull String method,
            @NotNull URI uri,
            String body
    ) throws NoSuchAlgorithmException, InvalidKeyException {
        String bearerToken = buildRestJwt(method, uri);
        return builder.header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
    }

    public String buildRestJwt(@NotNull String method, @NotNull URI uri)
            throws NoSuchAlgorithmException, InvalidKeyException {
        try {
            String validationError = CoinbaseCredentials.validationError(
                    credentials.apiKey(),
                    credentials.secret(),
                    ""
            );
            if (validationError != null) {
                throw new InvalidKeyException(validationError);
            }
            ECPrivateKey privateKey = (ECPrivateKey) loadPrivateKey(credentials.secret());
            JWSSigner signer = new ECDSASigner(privateKey);
            Instant now = Instant.now();
            String uriClaim = method.toUpperCase(Locale.ROOT) + " " + requestHost(uri) + requestPath(uri);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(credentials.apiKey())
                    .issuer("cdp")
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(TOKEN_TTL_SECONDS)))
                    .claim("uri", uriClaim)
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .type(JOSEObjectType.JWT)
                    .keyID(credentials.apiKey())
                    .customParam("nonce", nextNonce())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException | ClassCastException e) {
            throw new InvalidKeyException("Unable to sign Coinbase Advanced Trade JWT with the provided private key.", e);
        } catch (GeneralSecurityException e) {
            throw new InvalidKeyException("Unable to parse the Coinbase private key.", e);
        }
    }

    private static @NotNull String requestPath(@NotNull URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        return path.startsWith("/") ? path : "/" + path;
    }

    private static @NotNull String requestHost(@NotNull URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "api.coinbase.com";
        }
        return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
    }

    private static @NotNull String nextNonce() {
        byte[] bytes = new byte[16];
        NONCE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static @NotNull PrivateKey loadPrivateKey(String pem)
            throws GeneralSecurityException, NoSuchAlgorithmException {
        String normalizedPem = Objects.requireNonNull(pem, "pem must not be null").trim();
        String header = normalizedPem.lines()
                .filter(line -> line.startsWith("-----BEGIN "))
                .findFirst()
                .orElseThrow(() -> new InvalidKeyException("Coinbase private key PEM header is missing."));
        String footer = normalizedPem.lines()
                .filter(line -> line.startsWith("-----END "))
                .findFirst()
                .orElseThrow(() -> new InvalidKeyException("Coinbase private key PEM footer is missing."));

        String base64Body = normalizedPem
                .replace(header, "")
                .replace(footer, "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64Body);
        byte[] pkcs8Bytes = header.contains("EC PRIVATE KEY")
                ? wrapSec1EcKeyInPkcs8(keyBytes)
                : keyBytes;

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));
    }

    private static byte[] wrapSec1EcKeyInPkcs8(byte[] sec1Key) {
        byte[] algorithmIdentifier = derSequence(
                derOid("1.2.840.10045.2.1"),
                derOid("1.2.840.10045.3.1.7")
        );
        return derSequence(
                INTEGER_ZERO,
                algorithmIdentifier,
                derOctetString(sec1Key)
        );
    }

    private static byte[] derSequence(byte[]... elements) {
        byte[] content = join(elements);
        return derEncode((byte) 0x30, content);
    }

    private static byte[] derOctetString(byte[] content) {
        return derEncode((byte) 0x04, content);
    }

    private static byte[] derOid(String oid) {
        String[] parts = oid.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid OID: " + oid);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        out.write((first * 40) + second);
        for (int i = 2; i < parts.length; i++) {
            writeBase128(out, Long.parseLong(parts[i]));
        }
        return derEncode((byte) 0x06, out.toByteArray());
    }

    private static void writeBase128(ByteArrayOutputStream out, long value) {
        byte[] buffer = new byte[10];
        int index = buffer.length;
        buffer[--index] = (byte) (value & 0x7F);
        while ((value >>>= 7) > 0) {
            buffer[--index] = (byte) ((value & 0x7F) | 0x80);
        }
        out.write(buffer, index, buffer.length - index);
    }

    private static byte[] derEncode(byte tag, byte[] content) {
        byte[] length = derLength(content.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        out.writeBytes(length);
        out.writeBytes(content);
        return out.toByteArray();
    }

    private static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }

        int temp = length;
        int bytesNeeded = 0;
        while (temp > 0) {
            bytesNeeded++;
            temp >>= 8;
        }

        byte[] encoded = new byte[bytesNeeded + 1];
        encoded[0] = (byte) (0x80 | bytesNeeded);
        for (int i = bytesNeeded; i > 0; i--) {
            encoded[i] = (byte) (length & 0xFF);
            length >>= 8;
        }
        return encoded;
    }

    private static byte[] join(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            out.writeBytes(array);
        }
        return out.toByteArray();
    }
}
