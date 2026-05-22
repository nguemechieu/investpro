package org.investpro.activity.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.activity.BrokerActivityType;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SqliteBrokerActivityRepository implements BrokerActivityRepository {

    private final SqliteSchemaManager schema;

    public SqliteBrokerActivityRepository(SqliteSchemaManager schema) {
        this.schema = schema;
    }

    @Override
    public synchronized void save(BrokerActivityEvent event) {
        if (event == null) return;
        try {
            upsert(schema.getConnection(), event);
        } catch (SQLException e) {
            log.error("SqliteBrokerActivityRepository.save failed for event={}", event.getEventId(), e);
        }
    }

    @Override
    public synchronized void saveAll(List<BrokerActivityEvent> events) {
        if (events == null || events.isEmpty()) return;
        try {
            Connection conn = schema.getConnection();
            conn.setAutoCommit(false);
            try {
                for (BrokerActivityEvent e : events) {
                    if (e != null) upsert(conn, e);
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("SqliteBrokerActivityRepository.saveAll failed", e);
        }
    }

    private void upsert(Connection conn, BrokerActivityEvent e) throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO broker_activity_events
                (exchange_id, event_id, account_id, native_event_type, activity_type, order_id, trade_id, position_id,
                 trade_pair, side, requested_qty, filled_qty, remaining_qty, price, avg_fill_price, realized_pnl,
                 fee, fee_currency, financing, commission, unrealized_pnl, balance_before, balance_after,
                 balance_currency, margin_used, margin_available, event_time, received_at, cursor, source,
                 raw_json, terminal_event, error_event, projected, projection_error, reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getExchangeId());
            ps.setString(2, e.getEventId());
            ps.setString(3, e.getAccountId());
            ps.setString(4, e.getNativeEventType());
            ps.setString(5, e.getActivityType() == null ? null : e.getActivityType().name());
            ps.setString(6, e.getOrderId());
            ps.setString(7, e.getTradeId());
            ps.setString(8, e.getPositionId());
            ps.setString(9, e.getTradePair() == null ? null : e.getTradePair().toString());
            ps.setString(10, e.getSide() == null ? null : e.getSide().name());
            ps.setString(11, bd(e.getRequestedQuantity()));
            ps.setString(12, bd(e.getFilledQuantity()));
            ps.setString(13, bd(e.getRemainingQuantity()));
            ps.setString(14, bd(e.getPrice()));
            ps.setString(15, bd(e.getAverageFillPrice()));
            ps.setString(16, bd(e.getRealizedPnl()));
            ps.setString(17, bd(e.getFee()));
            ps.setString(18, e.getFeeCurrency());
            ps.setString(19, bd(e.getFinancing()));
            ps.setString(20, bd(e.getCommission()));
            ps.setString(21, bd(e.getUnrealizedPnl()));
            ps.setString(22, bd(e.getBalanceBefore()));
            ps.setString(23, bd(e.getBalanceAfter()));
            ps.setString(24, e.getBalanceCurrency());
            ps.setString(25, bd(e.getMarginUsed()));
            ps.setString(26, bd(e.getMarginAvailable()));
            ps.setString(27, e.getEventTime() == null ? null : e.getEventTime().toString());
            ps.setString(28, e.getReceivedAt() == null ? null : e.getReceivedAt().toString());
            ps.setString(29, e.getCursor());
            ps.setString(30, e.getSource());
            ps.setString(31, e.getRawJson());
            ps.setInt(32, e.isTerminalEvent() ? 1 : 0);
            ps.setInt(33, e.isErrorEvent() ? 1 : 0);
            ps.setInt(34, e.isProjected() ? 1 : 0);
            ps.setString(35, e.getProjectionError());
            ps.setString(36, e.getReason());
            ps.executeUpdate();
        }
    }

    private static String bd(BigDecimal val) {
        return val == null ? null : val.toPlainString();
    }

    @Override
    public synchronized Optional<BrokerActivityEvent> findByEventId(String exchangeId, String eventId) {
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND event_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, eventId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.error("findByEventId failed", e);
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<BrokerActivityEvent> findByTradePair(TradePair pair) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        if (pair == null) return result;
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE trade_pair=?")) {
            ps.setString(1, pair.toString());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findByTradePair failed", e);
        }
        return result;
    }

    @Override
    public synchronized List<BrokerActivityEvent> findByOrderId(String exchangeId, String accountId, String orderId) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND order_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, orderId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findByOrderId failed", e);
        }
        return result;
    }

    @Override
    public synchronized List<BrokerActivityEvent> findByTradeId(String exchangeId, String accountId, String tradeId) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND trade_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, tradeId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findByTradeId failed", e);
        }
        return result;
    }

    @Override
    public synchronized List<BrokerActivityEvent> findByPositionId(String exchangeId, String accountId, String positionId) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND position_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, positionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findByPositionId failed", e);
        }
        return result;
    }

    @Override
    public synchronized List<BrokerActivityEvent> findByTimeRange(String exchangeId, String accountId, Instant from, Instant to) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND event_time >= ? AND event_time <= ?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, from == null ? Instant.EPOCH.toString() : from.toString());
            ps.setString(3, to == null ? Instant.now().toString() : to.toString());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findByTimeRange failed", e);
        }
        return result;
    }

    @Override
    public synchronized List<BrokerActivityEvent> findUnprojectedEvents(String exchangeId, String accountId, int limit) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND projected=0 LIMIT ?")) {
            ps.setString(1, exchangeId);
            ps.setInt(2, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findUnprojectedEvents failed", e);
        }
        return result;
    }

    @Override
    public synchronized void markProjected(String exchangeId, String eventId, Instant projectedAt) {
        try (var ps = schema.getConnection().prepareStatement(
                "UPDATE broker_activity_events SET projected=1 WHERE exchange_id=? AND event_id=?")) {
            ps.setString(1, exchangeId);
            ps.setString(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("markProjected failed", e);
        }
    }

    @Override
    public synchronized void markProjectionFailed(String exchangeId, String eventId, String reason) {
        try (var ps = schema.getConnection().prepareStatement(
                "UPDATE broker_activity_events SET projected=0, projection_error=? WHERE exchange_id=? AND event_id=?")) {
            ps.setString(1, reason);
            ps.setString(2, exchangeId);
            ps.setString(3, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("markProjectionFailed failed", e);
        }
    }

    @Override
    public synchronized boolean exists(String exchangeId, String eventId) {
        try (var ps = schema.getConnection().prepareStatement(
                "SELECT 1 FROM broker_activity_events WHERE exchange_id=? AND event_id=? LIMIT 1")) {
            ps.setString(1, exchangeId);
            ps.setString(2, eventId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("exists failed", e);
        }
        return false;
    }

    private BrokerActivityEvent map(ResultSet rs) throws SQLException {
        BrokerActivityEvent.BrokerActivityEventBuilder b = BrokerActivityEvent.builder()
                .exchangeId(rs.getString("exchange_id"))
                .eventId(rs.getString("event_id"))
                .accountId(rs.getString("account_id"))
                .nativeEventType(rs.getString("native_event_type"))
                .orderId(rs.getString("order_id"))
                .tradeId(rs.getString("trade_id"))
                .positionId(rs.getString("position_id"))
                .feeCurrency(rs.getString("fee_currency"))
                .balanceCurrency(rs.getString("balance_currency"))
                .cursor(rs.getString("cursor"))
                .source(rs.getString("source"))
                .rawJson(rs.getString("raw_json"))
                .terminalEvent(rs.getInt("terminal_event") == 1)
                .errorEvent(rs.getInt("error_event") == 1)
                .projected(rs.getInt("projected") == 1)
                .projectionError(rs.getString("projection_error"))
                .reason(rs.getString("reason"));

        String actType = rs.getString("activity_type");
        if (actType != null) {
            try { b.activityType(BrokerActivityType.valueOf(actType)); } catch (IllegalArgumentException ignored) {}
        }

        String sideStr = rs.getString("side");
        if (sideStr != null) {
            try { b.side(Side.valueOf(sideStr)); } catch (IllegalArgumentException ignored) {}
        }

        String pairStr = rs.getString("trade_pair");
        if (pairStr != null) {
            try {
                String[] parts = pairStr.split("/");
                if (parts.length == 2) b.tradePair(TradePair.of(parts[0], parts[1]));
            } catch (Exception ignored) {}
        }

        b.requestedQuantity(parseBd(rs.getString("requested_qty")));
        b.filledQuantity(parseBd(rs.getString("filled_qty")));
        b.remainingQuantity(parseBd(rs.getString("remaining_qty")));
        b.price(parseBd(rs.getString("price")));
        b.averageFillPrice(parseBd(rs.getString("avg_fill_price")));
        b.realizedPnl(parseBd(rs.getString("realized_pnl")));
        b.fee(parseBd(rs.getString("fee")));
        b.financing(parseBd(rs.getString("financing")));
        b.commission(parseBd(rs.getString("commission")));
        b.unrealizedPnl(parseBd(rs.getString("unrealized_pnl")));
        b.balanceBefore(parseBd(rs.getString("balance_before")));
        b.balanceAfter(parseBd(rs.getString("balance_after")));
        b.marginUsed(parseBd(rs.getString("margin_used")));
        b.marginAvailable(parseBd(rs.getString("margin_available")));

        String evTime = rs.getString("event_time");
        if (evTime != null) { try { b.eventTime(Instant.parse(evTime)); } catch (Exception ignored) {} }
        String recAt = rs.getString("received_at");
        if (recAt != null) { try { b.receivedAt(Instant.parse(recAt)); } catch (Exception ignored) {} }

        return b.build();
    }

    private static BigDecimal parseBd(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }
}
