package org.investpro.exchange.credentials;

public record ExchangeCredentials(
        String exchangeId,
        String apiKey,
        String apiSecret,
        String keyName,
        String privateKey,
        String accessToken,
        String accountId,
        boolean sandbox
) {

    public boolean hasApiKeySecret() {
        return notBlank(apiKey) && notBlank(apiSecret);
    }

    public boolean hasCoinbaseAdvancedTradeCredentials() {
        return notBlank(keyName) && notBlank(privateKey);
    }

    public boolean hasOandaCredentials() {
        return notBlank(apiKey) && notBlank(accountId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}