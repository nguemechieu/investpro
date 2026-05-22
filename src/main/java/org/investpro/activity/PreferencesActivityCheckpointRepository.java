package org.investpro.activity;

import java.time.Instant;
import java.util.Optional;
import java.util.prefs.Preferences;

public class PreferencesActivityCheckpointRepository implements ActivityCheckpointRepository {
    private final Preferences preferences;

    public PreferencesActivityCheckpointRepository() {
        this(Preferences.userNodeForPackage(PreferencesActivityCheckpointRepository.class));
    }

    public PreferencesActivityCheckpointRepository(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public Optional<String> getLastCursor(String exchangeId, String accountId) {
        return Optional.ofNullable(preferences.get(key(exchangeId, accountId, "cursor"), null));
    }

    @Override
    public void saveLastCursor(String exchangeId, String accountId, String cursor) {
        if (cursor != null && !cursor.isBlank()) {
            preferences.put(key(exchangeId, accountId, "cursor"), cursor);
        }
    }

    @Override
    public Optional<Instant> getLastSyncTime(String exchangeId, String accountId) {
        String stored = preferences.get(key(exchangeId, accountId, "lastSyncTime"), null);
        if (stored == null) return Optional.empty();
        try {
            return Optional.of(Instant.parse(stored));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void saveLastSyncTime(String exchangeId, String accountId, Instant time) {
        preferences.put(key(exchangeId, accountId, "lastSyncTime"), (time == null ? Instant.now() : time).toString());
    }

    @Override
    public Optional<String> getLastProcessedFillId(String exchangeId, String accountId) {
        return Optional.ofNullable(preferences.get(key(exchangeId, accountId, "lastFillId"), null));
    }

    @Override
    public void saveLastProcessedFillId(String exchangeId, String accountId, String fillId) {
        if (fillId != null && !fillId.isBlank()) {
            preferences.put(key(exchangeId, accountId, "lastFillId"), fillId);
        }
    }

    private static String key(String exchangeId, String accountId, String suffix) {
        String exchange = clean(exchangeId);
        String account = clean(accountId == null || accountId.isBlank() ? "default" : accountId);
        return exchange + "." + account + "." + suffix;
    }

    private static String clean(String value) {
        return (value == null ? "unknown" : value).replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
