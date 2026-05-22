package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.activity.BrokerActivityType;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite-backed {@link BrokerActivityRepository}. Idempotent saves: duplicate
 * eventId per exchangeId is silently ignored (INSERT OR IGNORE).
 */
@Slf4j
public class SqliteBrokerActivityRepository implements BrokerActivityRepository {

    private final SqliteSchemaManager db;

    public SqliteBrokerActivityRepository(SqliteSchemaManager db) {
        this.db = db;
    }

    // ── Write ──────────────────────────────────────────────────────────────────

    @Override
    public void save(BrokerActivityEvent e) {
        if (e == null) return;
        String sql = """
                INSERT OR IGNORE INTO broker_activity_events(
                    exchange_id, account_id, event_id, native_type, activity_type,
                    order_id, trade_id, position_id, trade_pair, side,
                    requested_qty, filled_qty, remaining_qty, price, avg_fill_price,
                    realized_pnl, unrealized_pnl, fee, fee_currency, financing,
                    funding_fee, commission, balance_before, balance_after, balance_ccy,
                    margin_used, margin_avail, event_time, received_at,
                    cursor_val, source, raw_json, terminal, error_event,
                    reason, projected
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, e.getExchangeId());
            ps.setString(2, e.getAccountId());
            ps.setString(3, e.getEventId());
            ps.setString(4, e.getNativeEventType());
            ps.setString(5, e.getActivityType().name());
            ps.setString(6, e.getOrderId());
            ps.setString(7, e.getTradeId());
            ps.setString(8, e.getPositionId());
            ps.setString(9, pairStr(e.getTradePair()));
            ps.setString(10, e.getSide() == null ? null : e.getSide().name());
            ps.setString(11, bdStr(e.getRequestedQuantity()));
            ps.setString(12, bdStr(e.getFilledQuantity()));
            ps.setString(13, bdStr(e.getRemainingQuantity()));
            ps.setString(14, bdStr(e.getPrice()));
            ps.setString(15, bdStr(e.getAverageFillPrice()));
            ps.setString(16, bdStr(e.getRealizedPnl()));
            ps.setString(17, bdStr(e.getUnrealizedPnl()));
            ps.setString(18, bdStr(e.getFee()));
            ps.setString(19, e.getFeeCurrency());
            ps.setString(20, bdStr(e.getFinancing()));
            ps.setString(21, bdStr(e.getFundingFee()));
            ps.setString(22, bdStr(e.getCommission()));
            ps.setString(23, bdStr(e.getBalanceBefore()));
            ps.setString(24, bdStr(e.getBalanceAfter()));
            ps.setString(25, e.getBalanceCurrency());
            ps.setString(26, bdStr(e.getMarginUsed()));
            ps.setString(27, bdStr(e.getMarginAvailable()));
            ps.setString(28, e.getEventTime().toString());
            ps.setString(29, e.getReceivedAt().toString());
            ps.setString(30, e.getCursor());
            ps.setString(31, e.getSource());
            ps.setString(32, e.getRawJson());
            ps.setInt(33, e.isTerminalEvent() ? 1 : 0);
            ps.setInt(34, e.isErrorEvent() ? 1 : 0);
            ps.setString(35, e.getReason());
            ps.setInt(36, e.isProjected() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to save broker activity event {}/{}", e.getExchangeId(), e.getEventId(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void saveAll(List<BrokerActivityEvent> events) {
        if (events == null) return;
        events.forEach(this::save);
    }

    @Override
    public void markProjected(String exchangeId, String eventId, Instant projectedAt) {
        String sql = "UPDATE broker_activity_events SET projected=1, projected_at=?, projection_err=NULL WHERE exchange_id=? AND event_id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, projectedAt == null ? Instant.now().toString() : projectedAt.toString());
            ps.setString(2, exchangeId);
            ps.setString(3, eventId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("markProjected failed for {}/{}", exchangeId, eventId, ex);
        }
    }

    @Override
    public void markProjectionFailed(String exchangeId, String eventId, String reason) {
        String sql = "UPDATE broker_activity_events SET projected=0, projection_err=? WHERE exchange_id=? AND event_id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, exchangeId);
            ps.setString(3, eventId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("markProjectionFailed for {}/{}", exchangeId, eventId, ex);
        }
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Override
    public Optional<BrokerActivityEvent> findByEventId(String exchangeId, String eventId) {
        String sql = "SELECT * FROM broker_activity_events WHERE exchange_id=? AND event_id=? LIMIT 1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            ps.setString(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            log.error("findByEventId failed", ex);
            return Optional.empty();
        }
    }

    @Override
    public List<BrokerActivityEvent> findByTradePair(TradePair pair) {
        String sql = "SELECT * FROM broker_activity_events WHERE trade_pair=? ORDER BY event_time";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, pairStr(pair));
            return queryList(ps);
        } catch (SQLException ex) {
            log.error("findByTradePair failed", ex);
            return List.of();
        }
    }

    @Override
    public List<BrokerActivityEvent> findByOrderId(String exchangeId, String accountId, String orderId) {
        String sql = accountId == null
                ? "SELECT * FROM broker_activity_events WHERE exchange_id=? AND order_id=? ORDER BY event_time"
                : "SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND order_id=? ORDER BY event_time";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            if (accountId != null) { ps.setString(2, accountId); ps.setString(3, orderId); }
            else { ps.setString(2, orderId); }
            return queryList(ps);
        } catch (SQLException ex) {
            log.error("findByOrderId failed", ex);
            return List.of();
        }
    }

    @Override
    public List<BrokerActivityEvent> findByTradeId(String exchangeId, String accountId, String tradeId) {
        String sql = accountId == null
                ? "SELECT * FROM broker_activity_events WHERE exchange_id=? AND trade_id=? ORDER BY event_time"
                : "SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND trade_id=? ORDER BY event_time";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            if (accountId != null) { ps.setString(2, accountId); ps.setString(3, tradeId); }
            else { ps.setString(2, tradeId); }
            return queryList(ps);
        } catch (SQLException ex) {
            log.error("findByTradeId failed", ex);
            return List.of();
        }
    }

    @Override
    public List<BrokerActivityEvent> findByPositionId(String exchangeId, String accountId, String positionId) {
        String sql = accountId == null
                ? "SELECT * FROM broker_activity_events WHERE exchange_id=? AND position_id=? ORDER BY event_time"
                : "SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND position_id=? ORDER BY event_time";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            if (accountId != null) { ps.setString(2, accountId); ps.setString(3, positionId); }
            else { ps.setString(2, positionId); }
            return queryList(ps);
        } catch (SQLException ex) {
            log.error("findByPositionId failed", ex);
            return List.of();
        }
    }

    @Override
    public List<BrokerActivityEvent> findByTimeRange(String exchangeId, String accountId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        String sql = accountId == null
                ? "SELECT * FROM broker_activity_events WHERE exchange_id=? AND event_time>=? AND event_time<=? ORDER BY event_time"
                : "SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND event_time>=? AND event_time<=? ORDER BY event_time";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            if (accountId != null) { ps.setString(2, accountId); ps.setString(3, start.toString()); ps.setString(4, end.toString()); }
            else { ps.setString(2, start.toString()); ps.setString(3, end.toString()); }
            return queryList(ps);
        } catch (SQLException ex) {
            log.error("findByTimeRange failed", ex);
            return List.of();
        }
    }

    @Override
    public List<BrokerActivityEvent> findUnprojectedEvents(String exchangeId, String accountId, int limit) {
        String sql = accountId == null
                ? "SELECT * FROM broker_activity_events WHERE exchange_id=? AND projected=0 AND projection_err IS NULL ORDER BY event_time LIMIT ?"
                : "SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND projected=0 AND projection_err IS NULL ORDER BY event_time LIMIT ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            if (accountId != null) { ps.setString(2, accountId); ps.setInt(3, limit <= 0 ? Integer.MAX_VALUE : limit); }
            else { ps.setInt(2, limit <= 0 ? Integer.MAX_VALUE : limit); }
            return queryList(ps);
        } catch (SQLException ex) {
            log.error("findUnprojectedEvents failed", ex);
            return List.of();
        }
    }

    @Override
    public boolean exists(String exchangeId, String eventId) {
        String sql = "SELECT 1 FROM broker_activity_events WHERE exchange_id=? AND event_id=? LIMIT 1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, exchangeId);
            ps.setString(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<BrokerActivityEvent> queryList(PreparedStatement ps) throws SQLException {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        }
        return result;
    }

    private static BrokerActivityEvent map(ResultSet rs) throws SQLException {
        return BrokerActivityEvent.builder()
                .exchangeId(rs.getString("exchange_id"))
                .accountId(rs.getString("account_id"))
                .eventId(rs.getString("event_id"))
                .nativeEventType(rs.getString("native_type"))
                .activityType(parseType(rs.getString("activity_type")))
                .orderId(rs.getString("order_id"))
                .tradeId(rs.getString("trade_id"))
                .positionId(rs.getString("position_id"))
                .tradePair(parsePair(rs.getString("trade_pair")))
                .side(parseSide(rs.getString("side")))
                .requestedQuantity(bd(rs.getString("requested_qty")))
                .filledQuantity(bd(rs.getString("filled_qty")))
                .remainingQuantity(bd(rs.getString("remaining_qty")))
                .price(bd(rs.getString("price")))
                .averageFillPrice(bd(rs.getString("avg_fill_price")))
                .realizedPnl(bd(rs.getString("realized_pnl")))
                .unrealizedPnl(bd(rs.getString("unrealized_pnl")))
                .fee(bd(rs.getString("fee")))
                .feeCurrency(rs.getString("fee_currency"))
                .financing(bd(rs.getString("financing")))
                .fundingFee(bd(rs.getString("funding_fee")))
                .commission(bd(rs.getString("commission")))
                .balanceBefore(bd(rs.getString("balance_before")))
                .balanceAfter(bd(rs.getString("balance_after")))
                .balanceCurrency(rs.getString("balance_ccy"))
                .marginUsed(bd(rs.getString("margin_used")))
                .marginAvailable(bd(rs.getString("margin_avail")))
                .eventTime(parseInstant(rs.getString("event_time")))
                .receivedAt(parseInstant(rs.getString("received_at")))
                .cursor(rs.getString("cursor_val"))
                .source(rs.getString("source"))
                .rawJson(rs.getString("raw_json"))
                .terminalEvent(rs.getInt("terminal") == 1)
                .errorEvent(rs.getInt("error_event") == 1)
                .reason(rs.getString("reason"))
                .projected(rs.getInt("projected") == 1)
                .projectionError(rs.getString("projection_err"))
                .build();
    }

    private static BrokerActivityType parseType(String s) {
        if (s == null) return BrokerActivityType.UNKNOWN;
        try { return BrokerActivityType.valueOf(s); } catch (IllegalArgumentException e) { return BrokerActivityType.UNKNOWN; }
    }

    private static TradePair parsePair(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String[] parts = s.split("[/_]", 2);
            if (parts.length == 2) return TradePair.of(parts[0], parts[1]);
        } catch (Exception ignored) {}
        return null;
    }

    private static Side parseSide(String s) {
        if (s == null) return null;
        try { return Side.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        try { return Instant.parse(s); } catch (Exception e) { return Instant.now(); }
    }

    private static BigDecimal bd(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static String bdStr(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String pairStr(TradePair pair) {
        if (pair == null) return null;
        return pair.getBaseCode() + "/" + pair.getCounterCode();
    }
}
