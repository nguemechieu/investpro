package org.investpro.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.Db1;
import org.investpro.models.trading.TradeFee;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.RepositoryFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed persistence for {@link TradeFee} records.
 * <p>
 * The {@code trade_fees} table is created lazily on first use.
 * All query methods are read-only and return defensive copies.
 */
@Slf4j
public class TradeFeeRepository {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS trade_fees (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                exchange       TEXT    NOT NULL,
                base_currency  TEXT    NOT NULL,
                quote_currency TEXT    NOT NULL,
                order_id       TEXT,
                fee_amount     REAL    NOT NULL,
                fee_currency   TEXT    NOT NULL,
                fee_type       TEXT    NOT NULL,
                notional_value REAL    NOT NULL DEFAULT 0,
                trade_ts       TEXT    NOT NULL
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO trade_fees
                (exchange, base_currency, quote_currency, order_id,
                 fee_amount, fee_currency, fee_type, notional_value, trade_ts)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String CREATE_IDX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_tf_exchange_ts ON trade_fees (exchange, trade_ts)";

    private final Db1 db;

    public TradeFeeRepository() {
        this.db = RepositoryFactory.getDatabase();
        ensureSchema();
    }

    private void ensureSchema() {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(CREATE_TABLE_SQL)) {
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(CREATE_IDX_SQL)) {
                ps.execute();
            }
        } catch (SQLException e) {
            log.warn("Could not create trade_fees schema: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Write
    // ------------------------------------------------------------------

    /**
     * Persist a fee record and populate its generated {@code id}.
     */
    public void save(TradeFee fee) {
        if (fee == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            TradePair pair = fee.getTradePair();
            String base  = (pair != null) ? pair.getBaseCurrency().getCode()  : "";
            String quote = (pair != null) ? pair.getCounterCurrency().getCode() : "";

            ps.setString(1, fee.getExchange());
            ps.setString(2, base);
            ps.setString(3, quote);
            ps.setString(4, fee.getOrderId());
            ps.setDouble(5, fee.getAmount());
            ps.setString(6, fee.getFeeCurrency());
            ps.setString(7, fee.getFeeType() != null ? fee.getFeeType().name() : TradeFee.FeeType.UNKNOWN.name());
            ps.setDouble(8, fee.getNotionalValue());
            ps.setString(9, (fee.getTimestamp() != null ? fee.getTimestamp() : Instant.now()).toString());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) fee.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            log.warn("Failed to save TradeFee for exchange={}: {}", fee.getExchange(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Read helpers
    // ------------------------------------------------------------------

    /** All fees for a given exchange in descending time order. */
    public List<TradeFee> findByExchange(String exchange) {
        return query(
                "SELECT * FROM trade_fees WHERE exchange = ? ORDER BY trade_ts DESC",
                exchange);
    }

    /** All fees for a given exchange + trade pair (base/quote). */
    public List<TradeFee> findByExchangeAndPair(String exchange, TradePair pair) {
        String sql = "SELECT * FROM trade_fees " +
                     "WHERE exchange = ? AND base_currency = ? AND quote_currency = ? " +
                     "ORDER BY trade_ts DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exchange);
            ps.setString(2, pair.getBaseCurrency().getCode());
            ps.setString(3, pair.getCounterCurrency().getCode());
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (SQLException e) {
            log.warn("findByExchangeAndPair failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** All fees within a UTC date range (inclusive both ends). */
    public List<TradeFee> findByDateRange(Instant from, Instant to) {
        String sql = "SELECT * FROM trade_fees " +
                     "WHERE trade_ts >= ? AND trade_ts <= ? ORDER BY trade_ts DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (SQLException e) {
            log.warn("findByDateRange failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // Aggregation
    // ------------------------------------------------------------------

    /**
     * Total fee amount paid to an exchange, denominated in that exchange's fee currency.
     * If the exchange uses multiple fee currencies the result is the sum across all currencies
     * (use {@link #findByExchange} + group-by in application code for multi-currency breakdowns).
     */
    public double totalFeeByExchange(String exchange) {
        String sql = "SELECT COALESCE(SUM(fee_amount), 0) FROM trade_fees WHERE exchange = ?";
        return sumQuery(sql, exchange);
    }

    /** Total fees paid across ALL exchanges. */
    public double totalFeesAllExchanges() {
        String sql = "SELECT COALESCE(SUM(fee_amount), 0) FROM trade_fees";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            log.warn("totalFeesAllExchanges failed: {}", e.getMessage());
            return 0.0;
        }
    }

    /** Total fees paid today (UTC) on a given exchange. */
    public double totalFeeToday(String exchange) {
        String sql = "SELECT COALESCE(SUM(fee_amount), 0) FROM trade_fees " +
                     "WHERE exchange = ? AND date(trade_ts) = date('now')";
        return sumQuery(sql, exchange);
    }

    /** Count of fee records per exchange — useful for dashboards. */
    public long countByExchange(String exchange) {
        String sql = "SELECT COUNT(*) FROM trade_fees WHERE exchange = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exchange);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            log.warn("countByExchange failed: {}", e.getMessage());
            return 0L;
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private List<TradeFee> query(String sql, String param) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (SQLException e) {
            log.warn("TradeFeeRepository query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private double sumQuery(String sql, String param) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            log.warn("TradeFeeRepository sum query failed: {}", e.getMessage());
            return 0.0;
        }
    }

    private List<TradeFee> map(ResultSet rs) throws SQLException {
        List<TradeFee> result = new ArrayList<>();
        while (rs.next()) {
            TradeFee.FeeType feeType;
            try {
                feeType = TradeFee.FeeType.valueOf(rs.getString("fee_type"));
            } catch (IllegalArgumentException e) {
                feeType = TradeFee.FeeType.UNKNOWN;
            }

            String base  = rs.getString("base_currency");
            String quote = rs.getString("quote_currency");
            TradePair pair = null;
            try {
                if (base != null && quote != null) {
                    pair = TradePair.fromSymbol(base + "_" + quote);
                }
            } catch (Exception ignored) { /* pair lookup may fail for delisted symbols */ }

            result.add(TradeFee.builder()
                    .id(rs.getLong("id"))
                    .exchange(rs.getString("exchange"))
                    .tradePair(pair)
                    .orderId(rs.getString("order_id"))
                    .amount(rs.getDouble("fee_amount"))
                    .feeCurrency(rs.getString("fee_currency"))
                    .feeType(feeType)
                    .notionalValue(rs.getDouble("notional_value"))
                    .timestamp(Instant.parse(rs.getString("trade_ts")))
                    .build());
        }
        return result;
    }
}
