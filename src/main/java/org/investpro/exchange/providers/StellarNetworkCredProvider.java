package org.investpro.exchange.providers;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.exchange.credentials.ExchangeCredentials;


@Slf4j
@Getter
@Setter

public class StellarNetworkCredProvider {

    private ExchangeCredentials credentials;

    public StellarNetworkCredProvider(CredentialProvider provider) {
        this.credentials = new ExchangeCredentialResolver(provider).resolve("stellar-network");
        logCredentialStatus();
    }

    public String getKeyName() {
        return credentials.keyName();
    }

    public String getPrivateKey() {
        return credentials.privateKey();
    }

    public String getApiKey() {
        return credentials.apiKey();
    }

    public String getApiSecret() {
        return credentials.apiSecret();
    }

    public boolean hasAdvancedTradeCredentials() {
        return credentials.accountId() != null && !credentials.accountId().isBlank()
                && credentials.privateKey() != null && !credentials.privateKey().isBlank();
    }

    public boolean hasLegacyCredentials() {
        return credentials.hasApiKeySecret();
    }

    public void logCredentialStatus() {
        log.info("STELLAR NETWORK Credential Status:");
        log.info("  Stellar public/secret key: {}", hasAdvancedTradeCredentials() ? "CONFIGURED" : "NOT CONFIGURED");
        log.info("  Public account only: {}", credentials.accountId() != null && !credentials.accountId().isBlank()
                ? "CONFIGURED" : "NOT CONFIGURED");

        if (credentials.accountId() != null) {
            log.info("  Account ID: {}...", credentials.accountId().substring(0, Math.min(30, credentials.accountId().length())));
        }
    }
}
