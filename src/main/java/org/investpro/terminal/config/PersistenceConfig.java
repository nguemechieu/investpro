package org.investpro.terminal.config;

public record PersistenceConfig(
        String engine,
        String sqlitePath,
        boolean storeRawBrokerPayloads,
        boolean encryptSecretsAtRest
) {
    public static PersistenceConfig load() {
        return new PersistenceConfig(
                InvestProConfig.text("investpro.persistence.engine", "sqlite"),
                InvestProConfig.text("investpro.persistence.sqlitePath", "data/investpro-terminal.db"),
                InvestProConfig.bool("investpro.persistence.storeRawBrokerPayloads", true),
                InvestProConfig.bool("investpro.persistence.encryptSecretsAtRest", true));
    }
}
