package org.investpro.exchange.coinbase;

import com.nimbusds.jwt.SignedJWT;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.EdECPrivateKey;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinbaseApiCredentialsTest {
    private static final String KEY = "organizations/test/apiKeys/test";
    private static KeyPair edKey() throws Exception { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    private static String seed(KeyPair key) {
        return Base64.getEncoder().encodeToString(((EdECPrivateKey) key.getPrivate()).getBytes().orElseThrow());
    }
    private static void verify(CoinbaseJwtSigner signer, KeyPair key) throws Exception {
        SignedJWT jwt = SignedJWT.parse(signer.buildRestJwt("GET", "/api/v3/brokerage/accounts"));
        assertEquals("EdDSA", jwt.getHeader().getAlgorithm().getName());
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key.getPublic());
        verifier.update(jwt.getSigningInput());
        assertTrue(verifier.verify(jwt.getSignature().decode()));
        assertEquals(KEY, jwt.getJWTClaimsSet().getSubject());
    }
    @Test void signsRaw32ByteEd25519Secret() throws Exception {
        KeyPair key = edKey();
        verify(new CoinbaseJwtSigner(KEY, seed(key)), key);
    }
    @Test void signs64ByteSeedAndPublicKeyExport() throws Exception {
        KeyPair key = edKey();
        byte[] raw = new byte[64];
        System.arraycopy(((EdECPrivateKey) key.getPrivate()).getBytes().orElseThrow(), 0, raw, 0, 32);
        byte[] publicKey = key.getPublic().getEncoded();
        System.arraycopy(publicKey, publicKey.length - 32, raw, 32, 32);
        verify(new CoinbaseJwtSigner(KEY, "'" + Base64.getEncoder().encodeToString(raw) + "'"), key);
    }
    @Test void signsEd25519Pkcs8PemAndRedactsSigner() throws Exception {
        KeyPair key = edKey();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        CoinbaseJwtSigner signer = new CoinbaseJwtSigner(KEY, pem.replace("\n", "\\n"));
        verify(signer, key);
        assertEquals("CoinbaseJwtSigner[redacted]", signer.toString());
        assertNull(CoinbaseAuthProvider.validationError(KEY, seed(key), null));
    }
    @Test void rejectsUnsupportedEcCurve() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp384r1"));
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        assertThrows(IllegalArgumentException.class, () -> new CoinbaseJwtSigner(KEY, pem));
    }
    private static Coinbase exchange(KeyPair key, int status, String body) throws Exception {
        Coinbase exchange = new Coinbase(new ExchangeCredentials("coinbase", KEY, seed(key), null, null, null, null, true));
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (_, _) -> true));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            assertEquals("/api/v3/brokerage/accounts", request.uri().getPath());
            assertEquals("GET", request.method());
            assertNull(request.uri().getQuery());
            SignedJWT jwt = SignedJWT.parse(request.headers().firstValue("Authorization").orElseThrow().substring(7));
            assertEquals("GET api.coinbase.com/api/v3/brokerage/accounts", jwt.getJWTClaimsSet().getStringClaim("uri"));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key.getPublic()); verifier.update(jwt.getSigningInput());
            assertTrue(verifier.verify(jwt.getSignature().decode()));
            return response;
        });
        Field field = Coinbase.class.getDeclaredField("httpClient");
        field.setAccessible(true); field.set(exchange, client);
        return exchange;
    }
    @Test void connectsWithKeyAndSecretEvenWhenBotUsesPaperMode() throws Exception {
        Coinbase exchange = exchange(edKey(), 200, "{\"accounts\":[]}");
        assertTrue(exchange.checkAuthentication().isSuccess());
        assertTrue(exchange.AuthCheckResult("coinbase").success());
    }
    @Test void rejectsUnauthorizedCredentialsInsteadOfClaimingSuccess() throws Exception {
        var result = exchange(edKey(), 401, "Unauthorized").checkAuthentication();
        assertFalse(result.isSuccess()); assertEquals(401, result.getHttpStatus());
        assertTrue(result.isCredentialIssue());
    }
    @Test void reportsPortfolioPermissionFailure() throws Exception {
        var result = exchange(edKey(), 403, "Forbidden").checkAuthentication();
        assertFalse(result.isSuccess()); assertEquals(403, result.getHttpStatus());
        assertTrue(result.getMessage().contains("permission"));
    }
    @Test void rejectsMalformedSuccessResponse() throws Exception {
        assertFalse(exchange(edKey(), 200, "{}").checkAuthentication().isSuccess());
    }
}
