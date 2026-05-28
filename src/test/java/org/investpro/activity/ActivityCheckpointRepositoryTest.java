package org.investpro.activity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityCheckpointRepositoryTest {

    @Test
    void checkpointPersistsCursorAndLastSyncTime() {
        Preferences preferences = Preferences.userRoot().node("investpro-test-" + UUID.randomUUID());
        PreferencesActivityCheckpointRepository repository = new PreferencesActivityCheckpointRepository(preferences);
        Instant syncTime = Instant.parse("2026-05-22T10:15:30Z");

        repository.saveLastCursor("OANDA", "acct-1", "999");
        repository.saveLastSyncTime("OANDA", "acct-1", syncTime);

        assertEquals("999", repository.getLastCursor("OANDA", "acct-1").orElseThrow());
        assertEquals(syncTime, repository.getLastSyncTime("OANDA", "acct-1"));
    }
}
