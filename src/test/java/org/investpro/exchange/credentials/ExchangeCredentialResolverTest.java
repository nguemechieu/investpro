package org.investpro.exchange.credentials;

import org.investpro.exchange.contracts.CredentialProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeCredentialResolverTest {

    @Test
    void resolvesIbkrParamsIntoExchangeCredentials() {
        Map<String, String> values = Map.of(
                "IBKR_API_KEY", "ibkr-key",
                "IBKR_ACCESS_TOKEN", "ibkr-token",
                "IBKR_ACCOUNT_ID", "DU123456",
                "IBKR_ENVIRONMENT", "paper",
                "IBKR_HOST", "127.0.0.2",
                "IBKR_PORT", "7497",
                "IBKR_CLIENT_ID", "77",
                "IBKR_AUTH_MODE", "client-portal",
                "IBKR_CLIENT_PORTAL_URL", "https://localhost:5000/v1/api",
                "IBKR_ALLOW_COMPETE_TAKEOVER", "true");
        CredentialProvider provider = key -> Optional.ofNullable(values.get(key));

        ExchangeCredentials credentials = new ExchangeCredentialResolver(provider).resolve("ibkr");

        assertThat(credentials.exchangeId()).isEqualTo("interactive_brokers");
        assertThat(credentials.apiKey()).isEqualTo("ibkr-key");
        assertThat(credentials.accessToken()).isEqualTo("ibkr-token");
        assertThat(credentials.accountId()).isEqualTo("DU123456");
        assertThat(credentials.sandbox()).isTrue();
        assertThat(credentials.param("host")).isEqualTo("127.0.0.2");
        assertThat(credentials.intParamOrDefault("port", 0)).isEqualTo(7497);
        assertThat(credentials.intParamOrDefault("clientId", 0)).isEqualTo(77);
        assertThat(credentials.param("authMode")).isEqualTo("client-portal");
        assertThat(credentials.param("clientPortalUrl")).isEqualTo("https://localhost:5000/v1/api");
        assertThat(credentials.booleanParamOrDefault("allowCompeteTakeover", false)).isTrue();
    }
}
