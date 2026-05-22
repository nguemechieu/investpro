package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.ActivityCheckpointRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * SQLite-backed {@link ActivityCheckpointRepository}. Delegates to
 * {@link SqliteSchemaManager#upsertCheckpoint} and
 * {@link SqliteSchemaManager#readCheckpoint} for persistence.
 */
@Slf4j
public class SqliteActivityCheckpointRepository implements ActivityCheckpointRepository {

    private static final String KEY_CURSOR = "cursor";
    private static final String KEY_LAST_SYNC = "lastSyncTime";
    private static final String KEY_LAST_FILL = "lastFillId";

    private final SqliteSchemaManager db;

    public SqliteActivityCheckpointRepository(SqliteSchemaManager db) {
        this.db = db;
    }

    @Override
    public Optional<String> getLastCursor(String exchangeId, String accountId) {
        return Optional.ofNullable(db.readCheckpoint(exchangeId, accountId, KEY_CURSOR));
    }

    @Override
    public void saveLastCursor(String exchangeId, String accountId, String cursor) {
        if (cursor != null && !cursor.isBlank()) {
            db.upsertCheckpoint(exchangeId, accountId, KEY_CURSOR, cursor);
        }
    }

    @Override
    public Optional<Instant> getLastSyncTime(String exchangeId, String accountId) {
        String stored = db.readCheckpoint(exchangeId, accountId, KEY_LAST_SYNC);
        if (stored == null) return Optional.empty();
        try { return Optional.of(Instant.parse(stored)); } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public void saveLastSyncTime(String exchangeId, String accountId, Instant time) {
        db.upsertCheckpoint(exchangeId, accountId, KEY_LAST_SYNC,
                (time == null ? Instant.now() : time).toString());
    }

    @Override
    public Optional<String> getLastProcessedFillId(String exchangeId, String accountId) {
        return Optional.ofNullable(db.readCheckpoint(exchangeId, accountId, KEY_LAST_FILL));
    }

    @Override
    public void saveLastProcessedFillId(String exchangeId, String accountId, String fillId) {
        if (fillId != null && !fillId.isBlank()) {
            db.upsertCheckpoint(exchangeId, accountId, KEY_LAST_FILL, fillId);
        }
    }
}
