package org.investpro.utils;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinbaseJwtSignerTest {

    private static final String TEST_KEY_NAME = "organizations/test-org/apiKeys/test-key";

    @Test
    void buildsRestJwtWithCoinbaseRequiredClaims() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        CoinbaseJwtSigner signer = new CoinbaseJwtSigner(TEST_KEY_NAME, toPem(keyPair), 120);

        SignedJWT jwt = SignedJWT.parse(signer.buildRestJwt("GET", "/api/v3/brokerage/accounts"));

        assertTrue(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())));
        assertEquals(JWSAlgorithm.ES256, jwt.getHeader().getAlgorithm());
        assertEquals(TEST_KEY_NAME, jwt.getHeader().getKeyID());
        assertNotNull(jwt.getHeader().getCustomParam("nonce"));
        assertEquals("cdp", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(TEST_KEY_NAME, jwt.getJWTClaimsSet().getSubject());
        assertEquals("GET api.coinbase.com/api/v3/brokerage/accounts", jwt.getJWTClaimsSet().getStringClaim("uri"));
        assertNotNull(jwt.getJWTClaimsSet().getNotBeforeTime());
        assertNotNull(jwt.getJWTClaimsSet().getExpirationTime());
    }

    @Test
    void buildsWebSocketJwtWithoutUriClaim() throws Exception {
        CoinbaseJwtSigner signer = new CoinbaseJwtSigner(TEST_KEY_NAME, toPem(generateEcKeyPair()), 120);

        SignedJWT jwt = SignedJWT.parse(signer.buildWebSocketJwt());

        assertEquals("cdp", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(TEST_KEY_NAME, jwt.getJWTClaimsSet().getSubject());
        assertFalse(jwt.getJWTClaimsSet().getClaims().containsKey("uri"));
    }

    @Test
    void liveCredentialsCanCallAccountsEndpointWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("coinbase.liveAuth"), "live auth test disabled");

        String keyName = System.getenv("COINBASE_KEY_NAME");
        String privateKey = System.getenv("COINBASE_PRIVATE_KEY");
        Assumptions.assumeTrue(keyName != null && !keyName.isBlank(), "COINBASE_KEY_NAME missing");
        Assumptions.assumeTrue(privateKey != null && !privateKey.isBlank(), "COINBASE_PRIVATE_KEY missing");

        CoinbaseJwtSigner signer = new CoinbaseJwtSigner(keyName, privateKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.coinbase.com/api/v3/brokerage/accounts"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", signer.buildAuthorizationHeader("GET", "/api/v3/brokerage/accounts"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                "Coinbase accounts endpoint returned HTTP " + response.statusCode());
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String toPem(KeyPair keyPair) throws Exception {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }
}
