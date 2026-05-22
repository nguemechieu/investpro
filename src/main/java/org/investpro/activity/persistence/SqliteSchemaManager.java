package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Optional;

@Slf4j
public class SqliteSchemaManager {
    private final String jdbcUrl;

    public SqliteSchemaManager(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        }
        return conn;
    }

    public void runMigrations() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS broker_activity_events (
                    exchange_id TEXT NOT NULL,
                    event_id TEXT NOT NULL,
                    account_id TEXT,
                    native_event_type TEXT,
                    activity_type TEXT NOT NULL DEFAULT 'UNKNOWN',
                    order_id TEXT,
                    trade_id TEXT,
                    position_id TEXT,
                    trade_pair TEXT,
                    side TEXT,
                    requested_quantity TEXT,
                    filled_quantity TEXT,
                    remaining_quantity TEXT,
                    price TEXT,
                    average_fill_price TEXT,
                    realized_pnl TEXT,
                    fee TEXT,
                    fee_currency TEXT,
                    financing TEXT,
                    fund_fee TEXT,
                    commission TEXT,
                    unrealized_pnl TEXT,
                    balance_before TEXT,
                    balance_after TEXT,
                    balance_currency TEXT,
                    margin_used TEXT,
                    margin_available TEXT,
                    event_time TEXT,
                    received_at TEXT,
                    cursor_val TEXT,
                    source TEXT,
                    raw_json TEXT,
                    terminal_event INTEGER DEFAULT 0,
                    error_event INTEGER DEFAULT 0,
                    projected INTEGER DEFAULT 0,
                    projection_error TEXT,
                    reason TEXT,
                    PRIMARY KEY (exchange_id, event_id)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS activity_checkpoints (
                    exchange_id TEXT NOT NULL,
                    account_id TEXT NOT NULL,
                    key_name TEXT NOT NULL,
                    key_value TEXT,
                    PRIMARY KEY (exchange_id, account_id, key_name)
                )
                """);
            log.debug("Activity persistence schema migrated");
        } catch (SQLException e) {
            log.error("Failed to run activity schema migrations", e);
            throw new RuntimeException("Activity schema migration failed", e);
        }
    }

    public Optional<String> readCheckpoint(Connection conn, String exchangeId, String accountId, String keyName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT key_value FROM activity_checkpoints WHERE exchange_id=? AND account_id=? AND key_name=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId == null ? "default" : accountId);
            ps.setString(3, keyName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void upsertCheckpoint(Connection conn, String exchangeId, String accountId, String keyName, String keyValue) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO activity_checkpoints (exchange_id, account_id, key_name, key_value) VALUES (?,?,?,?)")) {
            ps.setString(1, exchangeId);
            ps.setString(2, accountId == null ? "default" : accountId);
            ps.setString(3, keyName);
            ps.setString(4, keyValue);
            ps.executeUpdate();
        }
    }
}
