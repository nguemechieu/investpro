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
public class CoinbaseCredProvider {

    private ExchangeCredentials credentials;

    public CoinbaseCredProvider(CredentialProvider provider) {
        this.credentials = new ExchangeCredentialResolver(provider).resolve("coinbase");
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
        return credentials.hasCoinbaseAdvancedTradeCredentials();
    }

    public boolean hasLegacyCredentials() {
        return credentials.hasApiKeySecret();
    }

    public void logCredentialStatus() {
        log.info("Coinbase Credential Status:");
        log.info("  Advanced Trade EC Key: {}", hasAdvancedTradeCredentials() ? "CONFIGURED" : "NOT CONFIGURED");
        log.info("  Legacy REST API: {}", hasLegacyCredentials() ? "CONFIGURED" : "NOT CONFIGURED");

        if (credentials.keyName() != null) {
            log.info("  Key Name: {}...", credentials.keyName().substring(0, Math.min(30, credentials.keyName().length())));
        }
    }
}