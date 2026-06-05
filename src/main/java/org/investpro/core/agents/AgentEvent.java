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
        Map<String, Object> metadata) {

    public static final String MARKET_TICK = "MARKET_TICK";
    public static final String MARKET_TRADE = "MARKET_TRADE";
    public static final String MARKET_CANDLE = "MARKET_CANDLE";
    public static final String ORDER_BOOK_UPDATE = "ORDER_BOOK_UPDATE";
    public static final String ACCOUNT_UPDATE = "ACCOUNT_UPDATE";
    public static final String BALANCE_UPDATE = "BALANCE_UPDATE";
    public static final String POSITION_UPDATE = "POSITION_UPDATE";
    public static final String ORDER_UPDATE = "ORDER_UPDATE";
    public static final String FILL_UPDATE = "FILL_UPDATE";
    public static final String POSITION_ACTION_APPROVED = "POSITION_ACTION_APPROVED";
    public static final String POSITION_ACTION_REJECTED = "POSITION_ACTION_REJECTED";
    public static final String POSITION_ACTION_RISKY = "POSITION_ACTION_RISKY";
    public static final String STREAM_CONNECTED = "STREAM_CONNECTED";
    public static final String STREAM_DISCONNECTED = "STREAM_DISCONNECTED";
    public static final String RAW_STREAM_MESSAGE = "RAW_STREAM_MESSAGE";

    public static final String SIGNAL_CREATED = "SIGNAL_CREATED";
    public static final String STRATEGY_SIGNAL_APPROVED = "STRATEGY_SIGNAL_APPROVED";
    public static final String STRATEGY_SIGNAL_REJECTED = "STRATEGY_SIGNAL_REJECTED";

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

    // ── Exchange resilience events
    // ──────────────────────────────────────────────────────
    /** Emitted when an exchange WebSocket or REST connection is established. */
    public static final String EXCHANGE_CONNECTED = "EXCHANGE_CONNECTED";
    /** Emitted when an exchange connection is lost. */
    public static final String EXCHANGE_DISCONNECTED = "EXCHANGE_DISCONNECTED";
    /** Emitted when a specific endpoint degrades below the health threshold. */
    public static final String ENDPOINT_DEGRADED = "ENDPOINT_DEGRADED";
    /** Emitted when an endpoint circuit breaker transitions to OPEN state. */
    public static final String CIRCUIT_OPENED = "CIRCUIT_OPENED";
    /** Emitted when an endpoint circuit breaker recovers to CLOSED state. */
    public static final String CIRCUIT_RECOVERED = "CIRCUIT_RECOVERED";
    /**
     * Emitted when the overall exchange health grade changes
     * (GREEN/YELLOW/ORANGE/RED).
     */
    public static final String EXCHANGE_HEALTH_CHANGED = "EXCHANGE_HEALTH_CHANGED";
    /** Emitted when a stale cached response is served instead of a live fetch. */
    public static final String STALE_CACHE_SERVED = "STALE_CACHE_SERVED";
    /**
     * Emitted when a reconciliation cycle completes (balances, positions, orders).
     */
    public static final String RECONCILIATION_COMPLETED = "RECONCILIATION_COMPLETED";
    /**
     * Emitted when an exchange degrades but remains operational (non-critical
     * endpoint failure).
     */
    public static final String EXCHANGE_DEGRADED = "EXCHANGE_DEGRADED";
    /** Emitted when authentication fails for an exchange. */
    public static final String EXCHANGE_AUTH_FAILED = "EXCHANGE_AUTH_FAILED";
    /** Emitted when WebSocket data exceeds the freshness threshold. */
    public static final String WEBSOCKET_STALE = "WEBSOCKET_STALE";
    /**
     * Emitted when the SmartExecutionRouter selects an execution route for an
     * order.
     */
    public static final String EXECUTION_ROUTE_SELECTED = "EXECUTION_ROUTE_SELECTED";
    /**
     * Emitted when the reconciliation engine detects drift between local and live
     * state.
     */
    public static final String RECONCILIATION_DRIFT_DETECTED = "RECONCILIATION_DRIFT_DETECTED";
    /** Emitted when the Solona RPC adapter connects successfully. */
    public static final String SOLONA_CONNECTED = "SOLONA_CONNECTED";
    /** Emitted when Solona wallet balance data is refreshed. */
    public static final String SOLONA_BALANCE_UPDATED = "SOLONA_BALANCE_UPDATED";
    /** Emitted when a Solona wallet transaction is detected. */
    public static final String SOLONA_TRANSACTION_DETECTED = "SOLONA_TRANSACTION_DETECTED";
    /** Emitted when a Solona transaction submission succeeds. */
    public static final String SOLONA_TRANSACTION_SUBMITTED = "SOLONA_TRANSACTION_SUBMITTED";
    /** Emitted when a Solona transaction submission fails. */
    public static final String SOLONA_TRANSACTION_FAILED = "SOLONA_TRANSACTION_FAILED";

    /** Emitted when a blockchain transaction is submitted to a chain network. */
    public static final String BLOCKCHAIN_TRANSACTION_SUBMITTED = "BLOCKCHAIN_TRANSACTION_SUBMITTED";
    /** Emitted when a blockchain transaction reaches confirmed finality. */
    public static final String BLOCKCHAIN_TRANSACTION_CONFIRMED = "BLOCKCHAIN_TRANSACTION_CONFIRMED";
    /** Emitted when a blockchain transaction fails or is rejected. */
    public static final String BLOCKCHAIN_TRANSACTION_FAILED = "BLOCKCHAIN_TRANSACTION_FAILED";
    /** Emitted when a blockchain transaction fails confirmation due to timeout. */
    public static final String BLOCKCHAIN_TRANSACTION_TIMEOUT = "BLOCKCHAIN_TRANSACTION_TIMEOUT";

    // ── Strategy lifecycle events
    // ───────────────────────────────────────────────────────
    /** Emitted when a strategy is assigned to a symbol/timeframe. */
    public static final String STRATEGY_ASSIGNED = "STRATEGY_ASSIGNED";
    /** Emitted when a strategy is promoted from paper to live trading. */
    public static final String STRATEGY_PROMOTED = "STRATEGY_PROMOTED";
    /** Emitted when a strategy is demoted from live back to paper trading. */
    public static final String STRATEGY_DEMOTED = "STRATEGY_DEMOTED";
    /** Emitted when a strategy is paused (no new signals processed). */
    public static final String STRATEGY_PAUSED = "STRATEGY_PAUSED";
    /** Emitted when a paused strategy is resumed. */
    public static final String STRATEGY_RESUMED = "STRATEGY_RESUMED";
    /** Emitted when a strategy is replaced by a new candidate. */
    public static final String STRATEGY_REPLACED = "STRATEGY_REPLACED";
    /** Emitted when a strategy is permanently archived. */
    public static final String STRATEGY_ARCHIVED = "STRATEGY_ARCHIVED";
    /**
     * Emitted when an AI health engine reports a change in strategy health level.
     */
    public static final String STRATEGY_HEALTH_CHANGED = "STRATEGY_HEALTH_CHANGED";
    /** Emitted when the learning engine updates a strategy's learning profile. */
    public static final String STRATEGY_LEARNING_UPDATED = "STRATEGY_LEARNING_UPDATED";
    /** Emitted when a strategy enters degraded state (health below threshold). */
    public static final String STRATEGY_DEGRADED = "STRATEGY_DEGRADED";
    /**
     * Emitted when a strategy generates a risk alert (drawdown, drawdown speed,
     * etc.).
     */
    public static final String STRATEGY_RISK_ALERT = "STRATEGY_RISK_ALERT";
    /** Emitted on any lifecycle status transition. */
    public static final String LIFECYCLE_STATE_CHANGED = "LIFECYCLE_STATE_CHANGED";
    /** Emitted when persisted strategy assignments are loaded during startup. */
    public static final String STRATEGY_ASSIGNMENTS_LOADED = "STRATEGY_ASSIGNMENTS_LOADED";
    /** Emitted when an assignment is recovered from persisted state. */
    public static final String STRATEGY_ASSIGNMENT_RECOVERED = "STRATEGY_ASSIGNMENT_RECOVERED";
    /** Emitted when an assignment is resumed after startup recovery. */
    public static final String STRATEGY_ASSIGNMENT_RESUMED = "STRATEGY_ASSIGNMENT_RESUMED";
    /**
     * Emitted when an assignment is flagged as needing review before live actions.
     */
    public static final String STRATEGY_ASSIGNMENT_NEEDS_REVIEW = "STRATEGY_ASSIGNMENT_NEEDS_REVIEW";
    /** Emitted when broker reconciliation detects an orphaned position. */
    public static final String STRATEGY_ORPHANED_POSITION_DETECTED = "STRATEGY_ORPHANED_POSITION_DETECTED";
    /** Emitted when reevaluation has been requested for an assignment/symbol. */
    public static final String STRATEGY_REEVALUATION_REQUESTED = "STRATEGY_REEVALUATION_REQUESTED";
    /**
     * Emitted when replacement is blocked by open-position ownership or safety
     * gates.
     */
    public static final String STRATEGY_REPLACEMENT_BLOCKED = "STRATEGY_REPLACEMENT_BLOCKED";
    /** Emitted whenever a strategy checkpoint is written. */
    public static final String STRATEGY_CHECKPOINT_SAVED = "STRATEGY_CHECKPOINT_SAVED";

    // ── AI review events
    // ────────────────────────────────────────────────────────────────
    /** Emitted when the AI completes a backtest review for a strategy. */
    public static final String AI_STRATEGY_BACKTEST_REVIEWED = "AI_STRATEGY_BACKTEST_REVIEWED";
    /** Emitted when the AI completes a validation review (paper trading phase). */
    public static final String AI_STRATEGY_VALIDATION_REVIEWED = "AI_STRATEGY_VALIDATION_REVIEWED";
    /** Emitted when the AI completes a signal review. */
    public static final String AI_SIGNAL_REVIEWED = "AI_SIGNAL_REVIEWED";
    /** Emitted when the AI replacement engine recommends replacing a strategy. */
    public static final String AI_REPLACEMENT_RECOMMENDED = "AI_REPLACEMENT_RECOMMENDED";
    /** Emitted when AI review approves a strategy lifecycle stage. */
    public static final String AI_STRATEGY_APPROVED = "AI_STRATEGY_APPROVED";
    /** Emitted when AI review rejects a strategy lifecycle stage. */
    public static final String AI_STRATEGY_REJECTED = "AI_STRATEGY_REJECTED";

    // Backward-compatible aliases used by legacy strategy modules
    public static final String AI_REPLACEMENT_EVALUATED = AI_REPLACEMENT_RECOMMENDED;
    public static final String AI_STRATEGY_VALIDATION_COMPLETED = AI_STRATEGY_VALIDATION_REVIEWED;
    public static final String STRATEGY_HEALTH_DEGRADED = STRATEGY_DEGRADED;

    // ── Pipeline events
    // ─────────────────────────────────────────────────────────────────
    /** Emitted when the pipeline approves a signal for position sizing. */
    public static final String SIGNAL_APPROVED = "SIGNAL_APPROVED";
    /** Emitted when the pipeline rejects a signal (AI veto or low confidence). */
    public static final String SIGNAL_REJECTED = "SIGNAL_REJECTED";
    /** Emitted when a full execution plan has been created and validated. */
    public static final String EXECUTION_PLAN_CREATED = "EXECUTION_PLAN_CREATED";

    // ── Portfolio intelligence events
    // ────────────────────────────────────────────────────
    /**
     * Emitted when the PortfolioIntelligenceEngine completes a portfolio analysis.
     */
    public static final String PORTFOLIO_ANALYZED = "PORTFOLIO_ANALYZED";

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
            Map<String, Object> metadata) {
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

    /**
     * Creates a lifecycle transition event.
     *
     * @param type    one of the STRATEGY_* or LIFECYCLE_* constants
     * @param source  component publishing the event
     * @param payload the lifecycle record or identifier
     * @return AgentEvent for the lifecycle transition
     */
    public static AgentEvent lifecycle(String type, String source, Object payload) {
        return of(type, source, payload);
    }

    /**
     * Creates a strategy health change event.
     *
     * @param source  component publishing the event (typically
     *                AIStrategyHealthEngine)
     * @param payload the StrategyHealthReport
     * @return AgentEvent for the health change
     */
    public static AgentEvent health(String source, Object payload) {
        return of(STRATEGY_HEALTH_CHANGED, source, payload);
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
