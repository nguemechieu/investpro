package org.investpro.investpro.services;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinbaseExchangeAuthTest {

    private static final String API_KEY = "organizations/test/apiKeys/key-1";
    private static final String PRIVATE_KEY = """
            -----BEGIN EC PRIVATE KEY-----
            MHcCAQEEIAqSV4qAfY1Nm0xd6k95EZ39suUWAuze5Vuhn671kB9OoAoGCCqGSM49
            AwEHoUQDQgAEcgYO1ly0wyz23wipRFpoM6Oyvh6WB1wy9EB8PHhrNw5VSJsAqsb7
            gc1E+mZ1HVX3H8eKNlw8GrQCQJsZ5ExllA==
            -----END EC PRIVATE KEY-----
            """;

    @Test
    void authorizeBuildsBearerJwtForAdvancedTradeRequests() throws Exception {
        CoinbaseExchangeAuth auth = new CoinbaseExchangeAuth(API_KEY, PRIVATE_KEY, "");
        URI uri = URI.create("https://api.coinbase.com/api/v3/brokerage/accounts");

        HttpRequest request = auth.authorize(HttpRequest.newBuilder(uri), "GET", uri, "")
                .GET()
                .build();

        String authorization = request.headers().firstValue("Authorization").orElseThrow();
        assertTrue(authorization.startsWith("Bearer "));
        assertFalse(request.headers().firstValue("CB-ACCESS-KEY").isPresent());

        SignedJWT jwt = SignedJWT.parse(authorization.substring("Bearer ".length()));
        assertEquals(API_KEY, jwt.getHeader().getKeyID());
        assertNotNull(jwt.getHeader().getCustomParam("nonce"));
        assertEquals(API_KEY, jwt.getJWTClaimsSet().getSubject());
        assertEquals("cdp", jwt.getJWTClaimsSet().getIssuer());
        assertEquals("GET api.coinbase.com/api/v3/brokerage/accounts", jwt.getJWTClaimsSet().getStringClaim("uri"));
    }
}
