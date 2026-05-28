package org.investpro.core.agents;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event used by the InvestPro agent runtime.
 */
public record AgentEvent(
        String type,
        String source,
        Object payload,
        Instant timestamp,
        Map<String, Object> metadata
) {

    public static final String MARKET_TICK = "MARKET_TICK";
    public static final String MARKET_TRADE = "MARKET_TRADE";
    public static final String MARKET_CANDLE = "MARKET_CANDLE";
    public static final String ORDER_BOOK_UPDATE = "ORDER_BOOK_UPDATE";
    public static final String ACCOUNT_UPDATE = "ACCOUNT_UPDATE";
    public static final String BALANCE_UPDATE = "BALANCE_UPDATE";
    public static final String POSITION_UPDATE = "POSITION_UPDATE";
    public static final String ORDER_UPDATE = "ORDER_UPDATE";
    public static final String FILL_UPDATE = "FILL_UPDATE";
    public static final String STREAM_CONNECTED = "STREAM_CONNECTED";
    public static final String STREAM_DISCONNECTED = "STREAM_DISCONNECTED";
    public static final String RAW_STREAM_MESSAGE = "RAW_STREAM_MESSAGE";

    public static final String SIGNAL_CREATED = "SIGNAL_CREATED";
    public static final String STRATEGY_SIGNAL_APPROVED = "STRATEGY_SIGNAL_APPROVED";
    public static final String STRATEGY_SIGNAL_REJECTED = "STRATEGY_SIGNAL_REJECTED";

    // ── Decision pipeline events ──────────────────────────────────────────────
    public static final String TRADE_INTENT_CREATED          = "TRADE_INTENT_CREATED";
    public static final String RISK_EVALUATION_COMPLETED     = "RISK_EVALUATION_COMPLETED";
    public static final String EXECUTION_PLAN_CREATED        = "EXECUTION_PLAN_CREATED";
    public static final String DECISION_APPROVED             = "DECISION_APPROVED";
    public static final String DECISION_REJECTED             = "DECISION_REJECTED";
    public static final String DECISION_EXECUTED             = "DECISION_EXECUTED";

    public static final String RISK_APPROVED = "RISK_APPROVED";
    public static final String RISK_REJECTED = "RISK_REJECTED";
    public static final String RISK_REVIEWED = "RISK_REVIEWED";
    public static final String RISK_ALERT = "RISK_ALERT";

    public static final String REASONING_APPROVED = "REASONING_APPROVED";
    public static final String REASONING_REJECTED = "REASONING_REJECTED";

    public static final String ORDER_SUBMITTED = "ORDER_SUBMITTED";
    public static final String ORDER_ACCEPTED = "ORDER_ACCEPTED";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    public static final String ORDER_FILLED = "ORDER_FILLED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String POSITION_CLOSED = "POSITION_CLOSED";

    public static final String PORTFOLIO_UPDATED = "PORTFOLIO_UPDATED";
    public static final String EXPOSURE_UPDATED = "EXPOSURE_UPDATED";
    public static final String PNL_UPDATED = "PNL_UPDATED";

    public static final String LEARNING_OBSERVATION_CREATED = "LEARNING_OBSERVATION_CREATED";

    public static final String SMART_BOT_STARTED = "SMART_BOT_STARTED";
    public static final String SMART_BOT_STREAMING_STARTED = "SMART_BOT_STREAMING_STARTED";
    public static final String SMART_BOT_STREAMING_STOPPED = "SMART_BOT_STREAMING_STOPPED";
    public static final String AUTO_TRADING_ENABLED = "AUTO_TRADING_ENABLED";
    public static final String AUTO_TRADING_DISABLED = "AUTO_TRADING_DISABLED";
    public static final String AI_REASONING_ENABLED = "AI_REASONING_ENABLED";
    public static final String AI_REASONING_DISABLED = "AI_REASONING_DISABLED";

    public static final String ERROR = "ERROR";

    // ── Solana blockchain network events ──────────────────────────────────────
    public static final String SOLANA_CONNECTED              = "SOLANA_CONNECTED";
    public static final String SOLANA_BALANCE_UPDATED        = "SOLANA_BALANCE_UPDATED";
    public static final String SOLANA_TRANSACTION_DETECTED   = "SOLANA_TRANSACTION_DETECTED";
    public static final String SOLANA_TRANSACTION_SUBMITTED  = "SOLANA_TRANSACTION_SUBMITTED";
    public static final String SOLANA_TRANSACTION_CONFIRMED  = "SOLANA_TRANSACTION_CONFIRMED";
    public static final String SOLANA_TRANSACTION_FAILED     = "SOLANA_TRANSACTION_FAILED";

    public AgentEvent {
        type = Objects.requireNonNull(type, "type must not be null").trim();

        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }

        source = source == null || source.isBlank() ? "unknown" : source.trim();
        timestamp = timestamp == null ? Instant.now() : timestamp;

        if (metadata == null || metadata.isEmpty()) {
            metadata = Map.of();
        } else {
            metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }

    public static AgentEvent of(String type, String source, Object payload) {
        return new AgentEvent(type, source, payload, Instant.now(), Map.of());
    }

    public static AgentEvent of(
            String type,
            String source,
            Object payload,
            Map<String, Object> metadata
    ) {
        return new AgentEvent(type, source, payload, Instant.now(), metadata);
    }

    public static AgentEvent market(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    public static AgentEvent signal(String source, Object payload) {
        return of(SIGNAL_CREATED, source, payload);
    }

    public static AgentEvent signal(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    public static AgentEvent risk(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    public static AgentEvent reasoning(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    public static AgentEvent execution(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    public static AgentEvent portfolio(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    public static AgentEvent learning(String source, Object payload) {
        return of(LEARNING_OBSERVATION_CREATED, source, payload);
    }

    public static AgentEvent error(String source, Throwable throwable, String message) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        if (message != null && !message.isBlank()) {
            metadata.put("message", message);
        }

        if (throwable != null) {
            metadata.put("exceptionType", throwable.getClass().getName());
            metadata.put("exceptionMessage", throwable.getMessage());
        }

        return new AgentEvent(ERROR, source, throwable, Instant.now(), metadata);
    }



    public Object metadataValue(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        return metadata.get(key);
    }





    public boolean isError() {
        return Objects.equals(type, ERROR);
    }

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return "AgentEvent{type='%s', source='%s', timestamp=%s, metadata=%s}"
                .formatted(type, source, timestamp, metadata);
    }
}
