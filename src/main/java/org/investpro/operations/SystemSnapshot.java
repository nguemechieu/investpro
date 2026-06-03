package org.investpro.operations;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Represents a complete snapshot of system state at a point in time.
 * Captures all relevant operational data for monitoring and diagnostics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSnapshot {

    // System Info
    @JsonProperty("snapshot_id")
    private String snapshotId;

    @JsonProperty("snapshot_timestamp")
    private Instant snapshotTimestamp;

    @JsonProperty("app_name")
    private String appName;

    @JsonProperty("app_version")
    @Nullable
    private String appVersion;

    @JsonProperty("startup_time")
    @Nullable
    private Instant startupTime;

    @JsonProperty("uptime_seconds")
    private long uptimeSeconds;

    @JsonProperty("active_profile")
    @Nullable
    private String activeProfile;

    @JsonProperty("java_version")
    private String javaVersion;

    @JsonProperty("java_vendor")
    private String javaVendor;

    @JsonProperty("os_name")
    private String osName;

    @JsonProperty("os_version")
    private String osVersion;

    @JsonProperty("available_processors")
    private int availableProcessors;

    @JsonProperty("memory_used_mb")
    private long memoryUsedMb;

    @JsonProperty("memory_max_mb")
    private long memoryMaxMb;

    @JsonProperty("memory_percent")
    private double memoryPercent;

    @JsonProperty("current_trading_mode")
    private String currentTradingMode;

    @JsonProperty("is_healthy")
    private boolean isHealthy;

    // Exchange Status
    @JsonProperty("exchanges")
    private List<ExchangeStatusSnapshot> exchanges;

    // Trading Engine Status
    @JsonProperty("trading_engine")
    private TradingEngineSnapshot tradingEngine;

    // Risk Status
    @JsonProperty("risk_status")
    private RiskStatusSnapshot riskStatus;

    // Health Summary
    @JsonProperty("error_count")
    private int errorCount;

    @JsonProperty("warning_count")
    private int warningCount;

    @JsonProperty("critical_count")
    private int criticalCount;

    // Runtime monitoring metrics
    @JsonProperty("heartbeat_age_seconds")
    @Nullable
    private Long heartbeatAgeSeconds;

    @JsonProperty("last_heartbeat_at")
    @Nullable
    private Instant lastHeartbeatAt;

    @JsonProperty("last_heartbeat_source")
    @Nullable
    private String lastHeartbeatSource;

    @JsonProperty("execution_error_count")
    private long executionErrorCount;

    @JsonProperty("account_error_count")
    private long accountErrorCount;

    @JsonProperty("websocket_disconnect_count")
    private long websocketDisconnectCount;

    /**
     * Exchange Status Snapshot
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExchangeStatusSnapshot {
        @JsonProperty("exchange_name")
        private String exchangeName;

        @JsonProperty("exchange_id")
        private String exchangeId;

        @JsonProperty("is_connected")
        private boolean isConnected;

        @JsonProperty("websocket_state")
        private String websocketState;

        @JsonProperty("rest_available")
        private boolean restAvailable;

        @JsonProperty("auth_status")
        private String authStatus;

        @JsonProperty("last_rest_request_time")
        @Nullable
        private Instant lastRestRequestTime;

        @JsonProperty("last_websocket_message_time")
        @Nullable
        private Instant lastWebsocketMessageTime;

        @JsonProperty("supported_market_types")
        private List<String> supportedMarketTypes;

        @JsonProperty("current_trade_pair")
        @Nullable
        private String currentTradePair;

        @JsonProperty("active_subscriptions")
        private int activeSubscriptions;

        @JsonProperty("open_streams")
        private int openStreams;

        @JsonProperty("last_error")
        @Nullable
        private String lastError;
    }

    /**
     * Trading Engine Status Snapshot
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradingEngineSnapshot {
        @JsonProperty("bot_trading_enabled")
        private boolean botTradingEnabled;

        @JsonProperty("signal_processor_state")
        private String signalProcessorState;

        @JsonProperty("risk_manager_state")
        private String riskManagerState;

        @JsonProperty("execution_engine_state")
        private String executionEngineState;

        @JsonProperty("strategy_engine_state")
        private String strategyEngineState;

        @JsonProperty("active_strategies")
        private List<String> activeStrategies;

        @JsonProperty("monitored_symbols")
        private List<String> monitoredSymbols;

        @JsonProperty("last_signal")
        @Nullable
        private String lastSignal;

        @JsonProperty("last_signal_time")
        @Nullable
        private Instant lastSignalTime;

        @JsonProperty("last_approved_trade")
        @Nullable
        private String lastApprovedTrade;

        @JsonProperty("last_approved_trade_time")
        @Nullable
        private Instant lastApprovedTradeTime;

        @JsonProperty("last_rejected_trade")
        @Nullable
        private String lastRejectedTrade;

        @JsonProperty("last_rejection_reason")
        @Nullable
        private String lastRejectionReason;

        @JsonProperty("signals_generated_today")
        private int signalsGeneratedToday;

        @JsonProperty("trades_approved_today")
        private int tradesApprovedToday;

        @JsonProperty("trades_rejected_today")
        private int tradesRejectedToday;
    }

    /**
     * Risk Status Snapshot
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskStatusSnapshot {
        @JsonProperty("account_balance")
        private double accountBalance;

        @JsonProperty("risk_per_trade_percent")
        private double riskPerTradePercent;

        @JsonProperty("max_position_size")
        private double maxPositionSize;

        @JsonProperty("max_daily_loss")
        @Nullable
        private Double maxDailyLoss;

        @JsonProperty("current_daily_loss")
        @Nullable
        private Double currentDailyLoss;

        @JsonProperty("portfolio_exposure_percent")
        @Nullable
        private Double portfolioExposurePercent;

        @JsonProperty("blocked_trades")
        private int blockedTrades;

        @JsonProperty("risk_warnings")
        private int riskWarnings;

        @JsonProperty("latest_rejection_reason")
        @Nullable
        private String latestRejectionReason;

        @JsonProperty("latest_rejection_time")
        @Nullable
        private Instant latestRejectionTime;
    }

    /**
     * Create a UUID-based snapshot ID
     */
    public void generateSnapshotId() {
        this.snapshotId = UUID.randomUUID().toString();
    }

    /**
     * Get a human-readable summary
     */
    public String getSummary() {
        return String.format(
                "Snapshot [%s] - %s | Uptime: %dh | Memory: %.1f%% | Exchanges: %d | Health: %s",
                snapshotId != null ? snapshotId.substring(0, 8) : "N/A",
                appName,
                uptimeSeconds / 3600,
                memoryPercent,
                exchanges != null ? exchanges.size() : 0,
                isHealthy ? "HEALTHY" : (criticalCount > 0 ? "CRITICAL" : "DEGRADED"));
    }

    /**
     * Check if system has critical issues
     */
    public boolean hasCriticalIssues() {
        return criticalCount > 0 || !isHealthy;
    }

    /**
     * Get health status badge
     */
    public String getHealthBadge() {
        if (criticalCount > 0)
            return "CRITICAL";
        if (errorCount > 0)
            return "DEGRADED";
        if (warningCount > 0)
            return "WARNING";
        return "HEALTHY";
    }
}
