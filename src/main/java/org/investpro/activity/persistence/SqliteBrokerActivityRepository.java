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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SqliteBrokerActivityRepository implements BrokerActivityRepository {
    private final SqliteSchemaManager schema;

    public SqliteBrokerActivityRepository(SqliteSchemaManager schema) {
        this.schema = schema;
        schema.runMigrations();
    }

    public SqliteBrokerActivityRepository(String dbPath) {
        this(new SqliteSchemaManager(dbPath));
    }

    @Override
    public void save(BrokerActivityEvent event) {
        if (event == null) return;
        String sql = """
            INSERT OR IGNORE INTO broker_activity_events
            (exchange_id, event_id, account_id, native_event_type, activity_type,
             order_id, trade_id, position_id, trade_pair, side,
             requested_quantity, filled_quantity, remaining_quantity, price, average_fill_price,
             realized_pnl, fee, fee_currency, financing, fund_fee, commission, unrealized_pnl,
             balance_before, balance_after, balance_currency, margin_used, margin_available,
             event_time, received_at, cursor_val, source, raw_json,
             terminal_event, error_event, projected, projection_error, reason)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = schema.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getExchangeId());
            ps.setString(2, event.getEventId());
            ps.setString(3, event.getAccountId());
            ps.setString(4, event.getNativeEventType());
            ps.setString(5, event.getActivityType() == null ? "UNKNOWN" : event.getActivityType().name());
            ps.setString(6, event.getOrderId());
            ps.setString(7, event.getTradeId());
            ps.setString(8, event.getPositionId());
            ps.setString(9, event.getTradePair() == null ? null : event.getTradePair().toString());
            ps.setString(10, event.getSide() == null ? null : event.getSide().name());
            ps.setString(11, bd(event.getRequestedQuantity()));
            ps.setString(12, bd(event.getFilledQuantity()));
            ps.setString(13, bd(event.getRemainingQuantity()));
            ps.setString(14, bd(event.getPrice()));
            ps.setString(15, bd(event.getAverageFillPrice()));
            ps.setString(16, bd(event.getRealizedPnl()));
            ps.setString(17, bd(event.getFee()));
            ps.setString(18, event.getFeeCurrency());
            ps.setString(19, bd(event.getFinancing()));
            ps.setString(20, bd(event.getFundingFee()));
            ps.setString(21, bd(event.getCommission()));
            ps.setString(22, bd(event.getUnrealizedPnl()));
            ps.setString(23, bd(event.getBalanceBefore()));
            ps.setString(24, bd(event.getBalanceAfter()));
            ps.setString(25, event.getBalanceCurrency());
            ps.setString(26, bd(event.getMarginUsed()));
            ps.setString(27, bd(event.getMarginAvailable()));
            ps.setString(28, event.getEventTime() == null ? null : event.getEventTime().toString());
            ps.setString(29, event.getReceivedAt() == null ? null : event.getReceivedAt().toString());
            ps.setString(30, event.getCursor());
            ps.setString(31, event.getSource());
            ps.setString(32, event.getRawJson());
            ps.setInt(33, event.isTerminalEvent() ? 1 : 0);
            ps.setInt(34, event.isErrorEvent() ? 1 : 0);
            ps.setInt(35, event.isProjected() ? 1 : 0);
            ps.setString(36, event.getProjectionError());
            ps.setString(37, event.getReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save BrokerActivityEvent {}:{}", event.getExchangeId(), event.getEventId(), e);
        }
    }

    @Override
    public void saveAll(List<BrokerActivityEvent> events) {
        if (events != null) events.forEach(this::save);
    }

    @Override
    public Optional<BrokerActivityEvent> findByEventId(String exchangeId, String eventId) {
        return queryOne("SELECT * FROM broker_activity_events WHERE exchange_id=? AND event_id=?", exchangeId, eventId);
    }

    @Override
    public List<BrokerActivityEvent> findByTradePair(TradePair pair) {
        if (pair == null) return List.of();
        return queryList("SELECT * FROM broker_activity_events WHERE trade_pair=? ORDER BY event_time", pair.toString());
    }

    @Override
    public List<BrokerActivityEvent> findByOrderId(String exchangeId, String accountId, String orderId) {
        if (accountId == null) {
            return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND order_id=? ORDER BY event_time", exchangeId, orderId);
        }
        return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND order_id=? ORDER BY event_time", exchangeId, accountId, orderId);
    }

    @Override
    public List<BrokerActivityEvent> findByTradeId(String exchangeId, String accountId, String tradeId) {
        if (accountId == null) {
            return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND trade_id=? ORDER BY event_time", exchangeId, tradeId);
        }
        return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND trade_id=? ORDER BY event_time", exchangeId, accountId, tradeId);
    }

    @Override
    public List<BrokerActivityEvent> findByPositionId(String exchangeId, String accountId, String positionId) {
        if (accountId == null) {
            return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND position_id=? ORDER BY event_time", exchangeId, positionId);
        }
        return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND position_id=? ORDER BY event_time", exchangeId, accountId, positionId);
    }

    @Override
    public List<BrokerActivityEvent> findByTimeRange(String exchangeId, String accountId, Instant from, Instant to) {
        String fromStr = (from == null ? Instant.EPOCH : from).toString();
        String toStr = (to == null ? Instant.now() : to).toString();
        if (accountId == null) {
            return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND event_time>=? AND event_time<=? ORDER BY event_time", exchangeId, fromStr, toStr);
        }
        return queryList("SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND event_time>=? AND event_time<=? ORDER BY event_time", exchangeId, accountId, fromStr, toStr);
    }

    @Override
    public List<BrokerActivityEvent> findUnprojectedEvents(String exchangeId, String accountId, int limit) {
        int lim = limit <= 0 ? Integer.MAX_VALUE : limit;
        if (accountId == null) {
            return queryList(
                "SELECT * FROM broker_activity_events WHERE exchange_id=? AND projected=0 AND projection_error IS NULL ORDER BY event_time LIMIT " + lim,
                exchangeId);
        }
        return queryList(
            "SELECT * FROM broker_activity_events WHERE exchange_id=? AND account_id=? AND projected=0 AND projection_error IS NULL ORDER BY event_time LIMIT " + lim,
            exchangeId, accountId);
    }

    @Override
    public void markProjected(String exchangeId, String eventId, Instant projectedAt) {
        execute("UPDATE broker_activity_events SET projected=1, projection_error=NULL WHERE exchange_id=? AND event_id=?", exchangeId, eventId);
    }

    @Override
    public void markProjectionFailed(String exchangeId, String eventId, String reason) {
        execute("UPDATE broker_activity_events SET projection_error=? WHERE exchange_id=? AND event_id=?", reason == null ? "unknown" : reason, exchangeId, eventId);
    }

    @Override
    public boolean exists(String exchangeId, String eventId) {
        return findByEventId(exchangeId, eventId).isPresent();
    }

    private Optional<BrokerActivityEvent> queryOne(String sql, String... params) {
        try (Connection conn = schema.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromRow(rs));
            }
        } catch (SQLException e) {
            log.error("queryOne error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private List<BrokerActivityEvent> queryList(String sql, String... params) {
        List<BrokerActivityEvent> result = new ArrayList<>();
        try (Connection conn = schema.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(fromRow(rs));
            }
        } catch (SQLException e) {
            log.error("queryList error: {}", e.getMessage());
        }
        return result;
    }

    private void execute(String sql, String... params) {
        try (Connection conn = schema.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("execute error: {}", e.getMessage());
        }
    }

    private static String bd(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal parseBdOrZero(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static BigDecimal parseBd(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return null; }
    }

    private static Instant parseInstant(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); } catch (Exception e) { return null; }
    }

    private static BrokerActivityEvent fromRow(ResultSet rs) throws SQLException {
        BrokerActivityType actType;
        try {
            actType = BrokerActivityType.valueOf(rs.getString("activity_type"));
        } catch (Exception e) {
            actType = BrokerActivityType.UNKNOWN;
        }
        Side side = null;
        String sideStr = rs.getString("side");
        if (sideStr != null) {
            try { side = Side.valueOf(sideStr); } catch (IllegalArgumentException ignored) {}
        }
        BrokerActivityEvent.BrokerActivityEventBuilder builder = BrokerActivityEvent.builder()
                .exchangeId(rs.getString("exchange_id"))
                .eventId(rs.getString("event_id"))
                .accountId(rs.getString("account_id"))
                .nativeEventType(rs.getString("native_event_type"))
                .activityType(actType)
                .orderId(rs.getString("order_id"))
                .tradeId(rs.getString("trade_id"))
                .positionId(rs.getString("position_id"))
                .side(side)
                .requestedQuantity(parseBdOrZero(rs.getString("requested_quantity")))
                .filledQuantity(parseBdOrZero(rs.getString("filled_quantity")))
                .remainingQuantity(parseBdOrZero(rs.getString("remaining_quantity")))
                .price(parseBd(rs.getString("price")))
                .averageFillPrice(parseBd(rs.getString("average_fill_price")))
                .realizedPnl(parseBdOrZero(rs.getString("realized_pnl")))
                .fee(parseBdOrZero(rs.getString("fee")))
                .feeCurrency(rs.getString("fee_currency"))
                .financing(parseBdOrZero(rs.getString("financing")))
                .fundingFee(parseBdOrZero(rs.getString("fund_fee")))
                .commission(parseBdOrZero(rs.getString("commission")))
                .unrealizedPnl(parseBdOrZero(rs.getString("unrealized_pnl")))
                .balanceBefore(parseBd(rs.getString("balance_before")))
                .balanceAfter(parseBd(rs.getString("balance_after")))
                .balanceCurrency(rs.getString("balance_currency"))
                .marginUsed(parseBd(rs.getString("margin_used")))
                .marginAvailable(parseBd(rs.getString("margin_available")))
                .cursor(rs.getString("cursor_val"))
                .source(rs.getString("source"))
                .rawJson(rs.getString("raw_json"))
                .terminalEvent(rs.getInt("terminal_event") == 1)
                .errorEvent(rs.getInt("error_event") == 1)
                .projected(rs.getInt("projected") == 1)
                .projectionError(rs.getString("projection_error"))
                .reason(rs.getString("reason"));
        Instant eventTime = parseInstant(rs.getString("event_time"));
        if (eventTime != null) builder.eventTime(eventTime);
        Instant receivedAt = parseInstant(rs.getString("received_at"));
        if (receivedAt != null) builder.receivedAt(receivedAt);
        String tradePairStr = rs.getString("trade_pair");
        if (tradePairStr != null && !tradePairStr.isBlank()) {
            try { builder.tradePair(TradePair.fromSymbol(tradePairStr)); }
            catch (Exception ignored) {}
        }
        return builder.build();
    }
}
