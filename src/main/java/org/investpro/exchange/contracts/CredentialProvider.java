package org.investpro.exchange.contracts;

import java.util.Optional;

public interface CredentialProvider {

    Optional<String> get(String key);

    default String getOrNull(String key) {
        return get(key).orElse(null);
    }

    default boolean has(String key) {
        return get(key)
                .map(value -> !value.isBlank())
                .orElse(false);
    }

    default boolean hasAll(String... keys) {
        if (keys == null || keys.length == 0) {
            return false;
        }

        for (String key : keys) {
            if (!has(key)) {
                return false;
            }
        }

        return true;
    }
}