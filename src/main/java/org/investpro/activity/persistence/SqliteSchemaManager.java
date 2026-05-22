package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection and schema migrations for the broker
 * activity event store. Thread-safe; one instance should be shared app-wide.
 */
@Slf4j
public class SqliteSchemaManager implements AutoCloseable {

    private static final String DEFAULT_DB_PATH = "data/investpro-activity.db";

    private final String jdbcUrl;
    private volatile Connection connection;

    public SqliteSchemaManager(String dbPath) {
        String path = (dbPath == null || dbPath.isBlank()) ? DEFAULT_DB_PATH : dbPath;
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public SqliteSchemaManager() {
        this(DEFAULT_DB_PATH);
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
            configurePragmas(connection);
        }
        return connection;
    }

    public synchronized void runMigrations() {
        try (Statement stmt = getConnection().createStatement()) {

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS broker_activity_events (
                        pk              INTEGER PRIMARY KEY AUTOINCREMENT,
                        exchange_id     TEXT NOT NULL,
                        account_id      TEXT,
                        event_id        TEXT NOT NULL,
                        native_type     TEXT,
                        activity_type   TEXT NOT NULL,
                        order_id        TEXT,
                        trade_id        TEXT,
                        position_id     TEXT,
                        trade_pair      TEXT,
                        side            TEXT,
                        requested_qty   TEXT,
                        filled_qty      TEXT,
                        remaining_qty   TEXT,
                        price           TEXT,
                        avg_fill_price  TEXT,
                        realized_pnl    TEXT,
                        unrealized_pnl  TEXT,
                        fee             TEXT,
                        fee_currency    TEXT,
                        financing       TEXT,
                        funding_fee     TEXT,
                        commission      TEXT,
                        balance_before  TEXT,
                        balance_after   TEXT,
                        balance_ccy     TEXT,
                        margin_used     TEXT,
                        margin_avail    TEXT,
                        event_time      TEXT NOT NULL,
                        received_at     TEXT NOT NULL,
                        cursor_val      TEXT,
                        source          TEXT,
                        raw_json        TEXT,
                        terminal        INTEGER NOT NULL DEFAULT 0,
                        error_event     INTEGER NOT NULL DEFAULT 0,
                        reason          TEXT,
                        projected       INTEGER NOT NULL DEFAULT 0,
                        projected_at    TEXT,
                        projection_err  TEXT,
                        UNIQUE(exchange_id, event_id)
                    )
                    """);

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bae_order ON broker_activity_events(exchange_id, account_id, order_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bae_trade ON broker_activity_events(exchange_id, account_id, trade_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bae_position ON broker_activity_events(exchange_id, account_id, position_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bae_time ON broker_activity_events(exchange_id, account_id, event_time)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bae_projected ON broker_activity_events(exchange_id, account_id, projected)");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS broker_activity_checkpoints (
                        pk              INTEGER PRIMARY KEY AUTOINCREMENT,
                        exchange_id     TEXT NOT NULL,
                        account_id      TEXT NOT NULL,
                        checkpoint_key  TEXT NOT NULL,
                        checkpoint_val  TEXT,
                        updated_at      TEXT NOT NULL,
                        UNIQUE(exchange_id, account_id, checkpoint_key)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_order_confirmations (
                        pk              INTEGER PRIMARY KEY AUTOINCREMENT,
                        exchange_id     TEXT NOT NULL,
                        account_id      TEXT,
                        client_order_id TEXT NOT NULL,
                        broker_order_id TEXT,
                        trade_pair      TEXT,
                        side            TEXT,
                        requested_qty   TEXT,
                        submitted_at    TEXT NOT NULL,
                        timeout_seconds INTEGER NOT NULL DEFAULT 30,
                        status          TEXT NOT NULL DEFAULT 'PENDING',
                        confirmed_at    TEXT,
                        error_reason    TEXT,
                        UNIQUE(exchange_id, client_order_id)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS reconciliation_runs (
                        pk              INTEGER PRIMARY KEY AUTOINCREMENT,
                        exchange_id     TEXT NOT NULL,
                        account_id      TEXT,
                        started_at      TEXT NOT NULL,
                        finished_at     TEXT,
                        status          TEXT NOT NULL DEFAULT 'RUNNING',
                        mismatches      INTEGER NOT NULL DEFAULT 0,
                        repaired        INTEGER NOT NULL DEFAULT 0,
                        error           TEXT
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS reconciliation_mismatches (
                        pk              INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_pk          INTEGER NOT NULL REFERENCES reconciliation_runs(pk),
                        exchange_id     TEXT NOT NULL,
                        account_id      TEXT,
                        mismatch_type   TEXT NOT NULL,
                        local_value     TEXT,
                        broker_value    TEXT,
                        entity_id       TEXT,
                        description     TEXT,
                        repaired        INTEGER NOT NULL DEFAULT 0,
                        repair_event_id TEXT,
                        detected_at     TEXT NOT NULL
                    )
                    """);

            log.info("SQLite broker activity schema migration complete: {}", jdbcUrl);

        } catch (SQLException e) {
            log.error("SQLite schema migration failed", e);
            throw new RuntimeException("Failed to initialise broker activity database", e);
        }
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) { } finally { connection = null; }
        }
    }

    private static void configurePragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    public void upsertCheckpoint(String exchangeId, String accountId, String key, String value) {
        String sql = """
                INSERT INTO broker_activity_checkpoints(exchange_id, account_id, checkpoint_key, checkpoint_val, updated_at)
                VALUES(?,?,?,?,datetime('now'))
                ON CONFLICT(exchange_id, account_id, checkpoint_key)
                DO UPDATE SET checkpoint_val=excluded.checkpoint_val, updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId == null ? "" : accountId);
            ps.setString(3, key);
            ps.setString(4, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert checkpoint {}/{}/{}", exchangeId, accountId, key, e);
            throw new RuntimeException(e);
        }
    }

    public String readCheckpoint(String exchangeId, String accountId, String key) {
        String sql = "SELECT checkpoint_val FROM broker_activity_checkpoints WHERE exchange_id=? AND account_id=? AND checkpoint_key=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId == null ? "" : accountId);
            ps.setString(3, key);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            log.error("Failed to read checkpoint {}/{}/{}", exchangeId, accountId, key, e);
            return null;
        }
    }
}
