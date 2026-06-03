package org.investpro.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionStartupValidatorTest {

    @Test
    void shouldFailProductionLiveStartupWhenDefaultExchangeSecretsAreMissing() {
        Map<String, String> config = new HashMap<>();
        config.put(AppConfigKeys.APP_ENV, "production");
        config.put(AppConfigKeys.DEFAULT_EXCHANGE, "BINANCE");
        config.put(AppConfigKeys.DEFAULT_ACCOUNT_MODE, "LIVE");
        config.put("AUTO_TRADING_ENABLED", "true");

        ProductionStartupValidator.StartupValidationReport report = ProductionStartupValidator.validate(config::get);

        assertThat(report.isValid()).isFalse();
        assertThat(report.errors())
                .anyMatch(error -> error.contains(AppConfigKeys.BINANCE_API_KEY))
                .anyMatch(error -> error.contains(AppConfigKeys.BINANCE_API_SECRET));
    }

    @Test
    void shouldAllowSafeProductionDefaultsWithoutSecretsInPaperMode() {
        Map<String, String> config = new HashMap<>();
        config.put(AppConfigKeys.APP_ENV, "production");
        config.put(AppConfigKeys.DEFAULT_EXCHANGE, "COINBASE");
        config.put(AppConfigKeys.DEFAULT_ACCOUNT_MODE, "PAPER");
        config.put("AUTO_TRADING_ENABLED", "false");

        ProductionStartupValidator.StartupValidationReport report = ProductionStartupValidator.validate(config::get);

        assertThat(report.isValid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }
}
