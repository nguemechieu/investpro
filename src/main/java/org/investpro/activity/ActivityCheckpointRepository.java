package org.investpro.activity;

import java.time.Instant;
import java.util.Optional;

public interface ActivityCheckpointRepository {
    Optional<String> getLastCursor(String exchangeId, String accountId);
    void saveLastCursor(String exchangeId, String accountId, String cursor);
    Optional<Instant> getLastSyncTime(String exchangeId, String accountId);
    void saveLastSyncTime(String exchangeId, String accountId, Instant time);
    Optional<String> getLastProcessedFillId(String exchangeId, String accountId);
    void saveLastProcessedFillId(String exchangeId, String accountId, String fillId);
}
