package org.investpro.terminal.persistence;

import java.util.Map;
import java.util.Optional;

public interface SettingsRepository {
    Optional<String> get(String key);
    void put(String key, String value);
    Map<String, String> snapshot();
}
