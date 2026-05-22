package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.ActivityCheckpointRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Slf4j
public class SqliteActivityCheckpointRepository implements ActivityCheckpointRepository {

    private final SqliteSchemaManager schema;

    public SqliteActivityCheckpointRepository(SqliteSchemaManager schema) {
        this.schema = schema;
    }

    @Override
    public Optional<String> getLastCursor(String exchangeId, String accountId) {
        String[] row = schema.readCheckpoint(exchangeId, accountId);
        if (row == null) return Optional.empty();
        return Optional.ofNullable(row[0]).filter(s -> !s.isBlank());
    }

    @Override
    public void saveLastCursor(String exchangeId, String accountId, String cursor) {
        schema.upsertCheckpoint(exchangeId, accountId, cursor, null);
    }

    @Override
    public Optional<Instant> getLastSyncTime(String exchangeId, String accountId) {
        String[] row = schema.readCheckpoint(exchangeId, accountId);
        if (row == null || row[1] == null || row[1].isBlank()) return Optional.empty();
        try {
            return Optional.of(Instant.parse(row[1]));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void saveLastSyncTime(String exchangeId, String accountId, Instant time) {
        try (var ps = schema.getConnection().prepareStatement("""
                INSERT INTO activity_checkpoints (exchange_id, account_id, last_sync_time)
                VALUES (?, ?, ?)
                ON CONFLICT(exchange_id, account_id) DO UPDATE SET last_sync_time = excluded.last_sync_time
                """)) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId);
            ps.setString(3, time == null ? null : time.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveLastSyncTime failed", e);
        }
    }

    @Override
    public Optional<String> getLastProcessedFillId(String exchangeId, String accountId) {
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT last_fill_id FROM activity_checkpoints WHERE exchange_id=? AND account_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("last_fill_id");
                    return Optional.ofNullable(val).filter(s -> !s.isBlank());
                }
            }
        } catch (SQLException e) {
            log.error("getLastProcessedFillId failed", e);
        }
        return Optional.empty();
    }

    @Override
    public void saveLastProcessedFillId(String exchangeId, String accountId, String fillId) {
        try (var ps = schema.getConnection().prepareStatement("""
                INSERT INTO activity_checkpoints (exchange_id, account_id, last_fill_id)
                VALUES (?, ?, ?)
                ON CONFLICT(exchange_id, account_id) DO UPDATE SET last_fill_id = excluded.last_fill_id
                """)) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId);
            ps.setString(3, fillId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveLastProcessedFillId failed", e);
        }
    }
}
