package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.ActivityCheckpointRepository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

@Slf4j
public class SqliteActivityCheckpointRepository implements ActivityCheckpointRepository {
    private final SqliteSchemaManager schema;

    public SqliteActivityCheckpointRepository(SqliteSchemaManager schema) {
        this.schema = schema;
    }

    public SqliteActivityCheckpointRepository(String dbPath) {
        this(new SqliteSchemaManager(dbPath));
    }

    @Override
    public Optional<String> getLastCursor(String exchangeId, String accountId) {
        try (Connection conn = schema.getConnection()) {
            return schema.readCheckpoint(conn, exchangeId, accountId, "cursor");
        } catch (SQLException e) {
            log.error("getLastCursor error", e);
            return Optional.empty();
        }
    }

    @Override
    public void saveLastCursor(String exchangeId, String accountId, String cursor) {
        if (cursor == null || cursor.isBlank()) return;
        try (Connection conn = schema.getConnection()) {
            schema.upsertCheckpoint(conn, exchangeId, accountId, "cursor", cursor);
        } catch (SQLException e) {
            log.error("saveLastCursor error", e);
        }
    }

    @Override
    public Optional<Instant> getLastSyncTime(String exchangeId, String accountId) {
        try (Connection conn = schema.getConnection()) {
            return schema.readCheckpoint(conn, exchangeId, accountId, "lastSyncTime")
                    .map(s -> { try { return Instant.parse(s); } catch (Exception e) { return null; } });
        } catch (SQLException e) {
            log.error("getLastSyncTime error", e);
            return Optional.empty();
        }
    }

    @Override
    public void saveLastSyncTime(String exchangeId, String accountId, Instant time) {
        try (Connection conn = schema.getConnection()) {
            schema.upsertCheckpoint(conn, exchangeId, accountId, "lastSyncTime",
                    (time == null ? Instant.now() : time).toString());
        } catch (SQLException e) {
            log.error("saveLastSyncTime error", e);
        }
    }

    @Override
    public Optional<String> getLastProcessedFillId(String exchangeId, String accountId) {
        try (Connection conn = schema.getConnection()) {
            return schema.readCheckpoint(conn, exchangeId, accountId, "lastFillId");
        } catch (SQLException e) {
            log.error("getLastProcessedFillId error", e);
            return Optional.empty();
        }
    }

    @Override
    public void saveLastProcessedFillId(String exchangeId, String accountId, String fillId) {
        if (fillId == null || fillId.isBlank()) return;
        try (Connection conn = schema.getConnection()) {
            schema.upsertCheckpoint(conn, exchangeId, accountId, "lastFillId", fillId);
        } catch (SQLException e) {
            log.error("saveLastProcessedFillId error", e);
        }
    }
}
