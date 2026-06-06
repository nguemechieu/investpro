package org.investpro.exchange.credentials;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record ExchangeCredentials(
        String exchangeId,
        String apiKey,
        String apiSecret,
        String keyName,
        String privateKey,
        String accessToken,
        String accountId,
        boolean sandbox,
        Map<String, String> params
) {

    public ExchangeCredentials(
            String exchangeId,
            String apiKey,
            String apiSecret,
            String keyName,
            String privateKey,
            String accessToken,
            String accountId,
            boolean sandbox) {
        this(exchangeId, apiKey, apiSecret, keyName, privateKey, accessToken, accountId, sandbox, Map.of());
    }

    public ExchangeCredentials {
        params = params == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(params));
    }

    public boolean hasApiKeySecret() {
        return notBlank(apiKey) && notBlank(apiSecret);
    }

    public boolean hasCoinbaseAdvancedTradeCredentials() {
        return notBlank(keyName) && notBlank(privateKey);
    }

    public boolean hasOandaCredentials() {
        return notBlank(apiKey) && notBlank(accountId);
    }

    public String param(String key) {
        if (key == null || params.isEmpty()) {
            return null;
        }
        String direct = params.get(key);
        if (notBlank(direct)) {
            return direct;
        }
        return params.get(key.trim().toLowerCase(Locale.ROOT));
    }

    public String paramOrDefault(String key, String fallback) {
        String value = param(key);
        return notBlank(value) ? value : fallback;
    }

    public int intParamOrDefault(String key, int fallback) {
        String value = param(key);
        if (!notBlank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean booleanParamOrDefault(String key, boolean fallback) {
        String value = param(key);
        return notBlank(value) ? Boolean.parseBoolean(value.trim()) : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
