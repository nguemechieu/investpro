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
    void explainsUnsupportedRawEd25519KeyWithoutExposingIt() {
        String secret = java.util.Base64.getEncoder().encodeToString(new byte[64]);
        String json = new JSONObject().put("name", NAME).put("privateKey", secret).toString();
        for (String input : new String[]{secret, json, json.substring(1, json.length() - 1)}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> CoinbaseCredentialInput.normalize(NAME, input));
            assertTrue(error.getMessage().contains("Ed25519"));
            assertTrue(error.getMessage().contains("ECDSA"));
            assertFalse(error.getMessage().contains(secret));
            assertFalse(error.getMessage().contains(NAME));
        }
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
