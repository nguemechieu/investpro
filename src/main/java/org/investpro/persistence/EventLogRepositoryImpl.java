package org.investpro.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.persistence.repository.RepositoryFactory;
import org.investpro.data.Db1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Persists {@link AgentEvent} records to the SQLite {@code event_log} table.
 * <p>
 * The table is created lazily on first use if it does not exist.
 */
@Slf4j
public class EventLogRepositoryImpl {

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS event_log (" +
            "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  event_type  TEXT    NOT NULL," +
            "  source      TEXT," +
            "  payload     TEXT," +
            "  metadata    TEXT," +
            "  occurred_at TEXT    NOT NULL" +
            ")";

    private static final String INSERT_SQL =
            "INSERT INTO event_log (event_type, source, payload, metadata, occurred_at) VALUES (?, ?, ?, ?, ?)";

    private final Db1 db;

    public EventLogRepositoryImpl() {
        this.db = RepositoryFactory.getDatabase();
        ensureTableExists();
    }

    private void ensureTableExists() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(CREATE_TABLE_SQL)) {
            ps.execute();
        } catch (SQLException e) {
            log.warn("Could not create event_log table: {}", e.getMessage());
        }
    }

    /**
     * Persist a single event to the database.
     */
    public void save(AgentEvent event) {
        if (event == null) {
            return;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, event.type());
            ps.setString(2, event.source());
            ps.setString(3, safePayload(event.payload()));
            ps.setString(4, event.metadata() != null ? event.metadata().toString() : null);
            ps.setString(5, (event.timestamp() != null ? event.timestamp() : Instant.now()).toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to save event type={}: {}", event.type(), e.getMessage());
        }
    }

    private String safePayload(Object payload) {
        if (payload == null) return null;
        try {
            String text = payload.toString();
            return text.length() > 2000 ? text.substring(0, 2000) : text;
        } catch (Exception e) {
            return payload.getClass().getSimpleName();
        }
    }
}
