package org.investpro.utils;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinbaseCredentialDiagnosticTest {
    @Test
    void diagnosticSignsEachExactRequestAndReportsQueryFailure() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            requests.add(request);
            HttpResponse<Void> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(request.uri().getRawQuery() == null ? 200 : 401);
            return response;
        });
        var results = CoinbaseCredentialDiagnostic.diagnose("organizations/test/apiKeys/test", pem.replace("\n", "\\n"), client);
        assertEquals(4, results.size());
        assertFalse(results.getLast().passed());
        assertEquals("products with query", results.getLast().step());
        assertEquals(401, results.getLast().statusCode());
        assertTrue(requests.getFirst().headers().firstValue("Authorization").isEmpty());
        Set<String> tokens = new HashSet<>();
        for (HttpRequest request : requests.subList(1, requests.size())) {
            String token = request.headers().firstValue("Authorization").orElseThrow().substring(7);
            assertTrue(tokens.add(token));
            assertEquals("GET api.coinbase.com" + request.uri().getRawPath(),
                    SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("uri"));
        }
    }
}
