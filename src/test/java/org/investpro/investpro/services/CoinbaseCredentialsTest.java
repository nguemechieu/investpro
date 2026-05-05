package org.investpro.investpro.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoinbaseCredentialsTest {

    @Test
    void normalizeAcceptsJsonBundleWithUuidAndPrivateKeyBody() {
        CoinbaseCredentials.Credentials credentials = CoinbaseCredentials.normalize(
                "",
                "{\"id\":\"2ffe3f58-d600-47a8-a147-1c55854eddc8\",\"privateKey\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}",
                ""
        );

        assertEquals("2ffe3f58-d600-47a8-a147-1c55854eddc8", credentials.apiKey());
        assertEquals(
                "-----BEGIN EC PRIVATE KEY-----\n"
                        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n"
                        + "-----END EC PRIVATE KEY-----\n",
                credentials.secret()
        );
        assertNull(CoinbaseCredentials.validationError("", credentials.secret(), credentials.apiKey()));
    }

    @Test
    void normalizeRestoresEscapedPemNewlines() {
        CoinbaseCredentials.Credentials credentials = CoinbaseCredentials.normalize(
                "organizations/test/apiKeys/key-1",
                "-----BEGIN EC PRIVATE KEY-----\\nline-1\\nline-2\\n-----END EC PRIVATE KEY-----\\n",
                ""
        );

        assertEquals("organizations/test/apiKeys/key-1", credentials.apiKey());
        assertEquals(
                "-----BEGIN EC PRIVATE KEY-----\n"
                        + "line-1\n"
                        + "line-2\n"
                        + "-----END EC PRIVATE KEY-----\n",
                credentials.secret()
        );
    }
}
