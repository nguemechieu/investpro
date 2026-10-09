package org.investpro.exchange.schwab;

import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

record SchwabApiConfig(
        String clientId,
        String clientSecret,
        String refreshToken,
        String accountId,
        String baseUrl,
        String traderApiBaseUrl,
        String marketDataBaseUrl,
        String oauthTokenUrl,
        boolean sandbox) {

    SchwabApiConfig {
        requireHttps(baseUrl); requireHttps(traderApiBaseUrl);
        requireHttps(marketDataBaseUrl); requireHttps(oauthTokenUrl);
    }

    static @NotNull SchwabApiConfig from(@NotNull ExchangeCredentials credentials) {
        String configuredBaseUrl = firstNonBlank(
                credentials.param("baseUrl"),
                AppConfig.get(AppConfigKeys.SCHWAB_BASE_URL),
                "https://api.schwabapi.com");
        String normalizedBase = trimTrailingSlash(configuredBaseUrl);

        String environment = firstNonBlank(
                credentials.param("environment"),
                AppConfig.get(AppConfigKeys.SCHWAB_ENVIRONMENT),
                "live");

        return new SchwabApiConfig(
                firstNonBlank(
                        credentials.param("clientId"),
                        credentials.apiKey(),
                        AppConfig.get(AppConfigKeys.SCHWAB_CLIENT_ID),
                        AppConfig.get("SCHWAB_API_KEY"),
                        ""),
                firstNonBlank(
                        credentials.param("clientSecret"),
                        credentials.apiSecret(),
                        AppConfig.get(AppConfigKeys.SCHWAB_CLIENT_SECRET),
                        AppConfig.get("SCHWAB_API_SECRET"),
                        ""),
                firstNonBlank(
                        credentials.param("refreshToken"),
                        credentials.accessToken(),
                        AppConfig.get(AppConfigKeys.SCHWAB_REFRESH_TOKEN),
                        ""),
                firstNonBlank(
                        credentials.param("accountId"),
                        credentials.accountId(),
                        AppConfig.get(AppConfigKeys.SCHWAB_ACCOUNT_ID),
                        ""),
                normalizedBase,
                firstNonBlank(
                        credentials.param("traderApiBaseUrl"),
                        normalizedBase + "/trader/v1"),
                firstNonBlank(
                        credentials.param("marketDataBaseUrl"),
                        normalizedBase + "/marketdata/v1"),
                firstNonBlank(
                        credentials.param("oauthTokenUrl"),
                        AppConfig.get("SCHWAB_TOKEN_URL"),
                        normalizedBase + "/v1/oauth/token"),
                credentials.sandbox() || isPaperLike(environment));
    }

    boolean hasRequiredCredentials() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    java.net.URI redirectUri() { return requireHttps(AppConfig.get(AppConfigKeys.SCHWAB_REDIRECT_URI)); }

    java.net.URI authorizationUri() {
        // Supply the endpoint from the approved product's official OAuth documentation.
        return requireHttps(AppConfig.get("SCHWAB_AUTHORIZATION_URL"));
    }

    static java.net.URI requireHttps(String value) {
        try {
            var uri = java.net.URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
                throw new IllegalArgumentException();
            return uri;
        } catch (Exception error) { throw new IllegalArgumentException("Schwab requires a valid HTTPS endpoint or callback URL, without credentials, fragments, or query parameters."); }
    }

    @Override public String toString() { return "SchwabApiConfig[credentials=<redacted>]"; }

    private static boolean isPaperLike(String value) {
        if (!notBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "paper".equals(normalized)
                || "sandbox".equals(normalized)
                || "demo".equals(normalized)
                || "test".equals(normalized)
                || "testnet".equals(normalized);
    }

    private static String trimTrailingSlash(String value) {
        String current = value == null ? "" : value.trim();
        while (current.endsWith("/")) {
            current = current.substring(0, current.length() - 1);
        }
        return current;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
