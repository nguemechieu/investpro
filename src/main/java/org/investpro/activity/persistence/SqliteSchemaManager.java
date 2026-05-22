package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

@Slf4j
public class SqliteSchemaManager {

    private static final String DEFAULT_DB_PATH = "data/investpro-activity.db";

    private final String dbPath;
    private Connection connection;

    public SqliteSchemaManager(String dbPath) {
        this.dbPath = (dbPath != null && !dbPath.isBlank()) ? dbPath : DEFAULT_DB_PATH;
    }

    public SqliteSchemaManager() {
        this(DEFAULT_DB_PATH);
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found", e);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
        }
        return connection;
    }

    public synchronized void runMigrations() throws SQLException {
        Connection conn = getConnection();
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS broker_activity_events (
                        id INTEGER PRIMARY KEY,
                        exchange_id TEXT NOT NULL,
                        event_id TEXT NOT NULL,
                        account_id TEXT,
                        native_event_type TEXT,
                        activity_type TEXT,
                        order_id TEXT,
                        trade_id TEXT,
                        position_id TEXT,
                        trade_pair TEXT,
                        side TEXT,
                        requested_qty TEXT,
                        filled_qty TEXT,
                        remaining_qty TEXT,
                        price TEXT,
                        avg_fill_price TEXT,
                        realized_pnl TEXT,
                        fee TEXT,
                        fee_currency TEXT,
                        financing TEXT,
                        commission TEXT,
                        unrealized_pnl TEXT,
                        balance_before TEXT,
                        balance_after TEXT,
                        balance_currency TEXT,
                        margin_used TEXT,
                        margin_available TEXT,
                        event_time TEXT,
                        received_at TEXT,
                        cursor TEXT,
                        source TEXT,
                        raw_json TEXT,
                        terminal_event INTEGER,
                        error_event INTEGER,
                        projected INTEGER,
                        projection_error TEXT,
                        reason TEXT,
                        UNIQUE(exchange_id, event_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS activity_checkpoints (
                        id INTEGER PRIMARY KEY,
                        exchange_id TEXT NOT NULL,
                        account_id TEXT NOT NULL,
                        last_cursor TEXT,
                        last_sync_time TEXT,
                        last_fill_id TEXT,
                        UNIQUE(exchange_id, account_id)
                    )
                    """);
        }
        log.info("SqliteSchemaManager: migrations complete for db={}", dbPath);
    }

    public synchronized void upsertCheckpoint(String exchangeId, String accountId, String cursor, Instant syncTime) {
        try (var ps = getConnection().prepareStatement("""
                INSERT INTO activity_checkpoints (exchange_id, account_id, last_cursor, last_sync_time)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(exchange_id, account_id) DO UPDATE SET
                    last_cursor = excluded.last_cursor,
                    last_sync_time = excluded.last_sync_time
                """)) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId);
            ps.setString(3, cursor);
            ps.setString(4, syncTime == null ? null : syncTime.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("SqliteSchemaManager.upsertCheckpoint failed", e);
        }
    }

    public synchronized String[] readCheckpoint(String exchangeId, String accountId) {
        try (var ps = getConnection().prepareStatement(
                "SELECT last_cursor, last_sync_time FROM activity_checkpoints WHERE exchange_id=? AND account_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{rs.getString("last_cursor"), rs.getString("last_sync_time")};
                }
            }
        } catch (SQLException e) {
            log.error("SqliteSchemaManager.readCheckpoint failed", e);
        }
        return null;
    }
}
