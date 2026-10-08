package org.investpro.exchange.coinbase;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoinbaseCredentialInputTest {
    private static final String NAME = "organizations/org/apiKeys/key";
    private static final String PEM = "-----BEGIN EC PRIVATE KEY-----\nYWJj\n-----END EC PRIVATE KEY-----";

    @Test
    void normalizesQuotedAssignmentsAndEscapedNewlines() {
        var input = CoinbaseCredentialInput.normalize("export COINBASE_KEY_NAME = '" + NAME + "'",
                "COINBASE_PRIVATE_KEY=\"" + PEM.replace("\n", "\\n") + "\"");
        assertEquals(NAME, input.keyName());
        assertEquals(PEM, input.privateKey());
        assertFalse(input.toString().contains("YWJj"));
    }

    @Test
    void extractsDownloadedJsonFromEitherField() {
        String json = new JSONObject().put("name", NAME).put("privateKey", PEM).toString();
        assertEquals(PEM, CoinbaseCredentialInput.normalize(json, "").privateKey());
        assertEquals(NAME, CoinbaseCredentialInput.normalize("", json).keyName());
    }

    @Test
    void restoresFlattenedPem() {
        assertEquals(PEM, CoinbaseCredentialInput.normalize(NAME, PEM.replace('\n', ' ')).privateKey());
    }

    @Test
    void acceptsExportFieldsWithoutOuterBraces() {
        String json = new JSONObject().put("name", NAME).put("privateKey", PEM).toString();
        String fragment = json.substring(1, json.length() - 1);
        assertEquals(NAME, CoinbaseCredentialInput.normalize(fragment, "").keyName());
        assertEquals(PEM, CoinbaseCredentialInput.normalize("", fragment).privateKey());
    }

    @Test
    void acceptsRawEd25519KeyAndJsonExportWithoutExposingIt() {
        String secret = java.util.Base64.getEncoder().encodeToString(new byte[64]);
        String json = new JSONObject().put("name", NAME).put("privateKey", secret).toString();
        for (String input : new String[]{secret, json, json.substring(1, json.length() - 1)}) {
            var normalized = CoinbaseCredentialInput.normalize(NAME, input);
            assertEquals(secret, normalized.privateKey());
            assertFalse(normalized.toString().contains(secret));
        }
    }
    @Test
    void acceptsIndividuallyPastedJsonFields() {
        assertEquals(NAME, CoinbaseCredentialInput.normalize("\"name\": \"" + NAME + "\",",
                "\"privateKey\": " + JSONObject.quote(PEM) + ",").keyName());
        assertEquals(PEM, CoinbaseCredentialInput.normalize("\"name\": \"" + NAME + "\",",
                "\"privateKey\": " + JSONObject.quote(PEM) + ",").privateKey());
        assertEquals(PEM, CoinbaseCredentialInput.normalize("\"name\": \"" + NAME + "\"",
                "\"privateKey\": " + JSONObject.quote(PEM)).privateKey());
    }

    @Test
    void restoresBarePkcs8KeyAndProducesVerifiableCoinbaseJwt() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        String body = java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        var normalized = CoinbaseCredentialInput.normalize(NAME, body);
        assertTrue(normalized.privateKey().startsWith("-----BEGIN PRIVATE KEY-----"));
        var signer = new CoinbaseJwtSigner(normalized.keyName(), normalized.privateKey());
        var jwt = com.nimbusds.jwt.SignedJWT.parse(signer.buildRestJwt("GET", "api.coinbase.com", "/api/v3/brokerage/accounts"));
        assertTrue(jwt.verify(new com.nimbusds.jose.crypto.ECDSAVerifier((java.security.interfaces.ECPublicKey) pair.getPublic())));
    }

    @Test
    void handlesDoublyEscapedPemWithoutChangingKeyMaterial() {
        assertEquals(PEM, CoinbaseCredentialInput.normalize(NAME, PEM.replace("\n", "\\\\n")).privateKey());
    }

    @Test
    void uiCredentialsOverrideStaleAliasesAndRemainPaired() {
        var provider = new org.investpro.exchange.providers.UiCredentialProvider("coinbase", NAME,
                PEM.replace('\n', ' '), "", "LIVE", "", java.util.Map.of(
                "COINBASE_KEY_NAME", "stale-name", "COINBASE_PRIVATE_KEY", "stale-secret"));
        var credentials = new org.investpro.exchange.credentials.ExchangeCredentialResolver(provider).resolve("coinbase");
        assertEquals(NAME, credentials.keyName());
        assertEquals(PEM, credentials.privateKey());
        assertEquals(NAME, credentials.apiKey());
        assertEquals(PEM, credentials.apiSecret());
    }

    @Test
    void sharedUiValidationAcceptsRawEd25519AndRejectsInvalidSecretsSafely() throws Exception {
        var pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] pkcs8 = pair.getPrivate().getEncoded();
        String secret = java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(pkcs8, pkcs8.length - 32, pkcs8.length));
        assertNull(CoinbaseCredentialInput.validationError(NAME, secret));
        String error = CoinbaseCredentialInput.validationError(NAME, "sensitive-malformed-secret");
        assertNotNull(error);
        assertFalse(error.contains("sensitive-malformed-secret"));
    }

    @Test
    void doesNotInventMissingDataOrExposeMalformedExport() {
        assertEquals("short-id", CoinbaseCredentialInput.normalize("short-id", "").keyName());
        assertEquals("", CoinbaseCredentialInput.normalize("", "").privateKey());
        var error = assertThrows(IllegalArgumentException.class,
                () -> CoinbaseCredentialInput.normalize("", "{\"privateKey\":\"sensitive\"}"));
        assertFalse(error.getMessage().contains("sensitive"));
    }
}
