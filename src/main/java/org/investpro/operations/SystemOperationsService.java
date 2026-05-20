package org.investpro.operations;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.ExchangeIdentity;
import org.investpro.exchange.services.ExchangeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/**
 * Service that observes and collects system state from existing services.
 * Does not create services - only observes existing ones.
 * Thread-safe and non-blocking. Supports optional data providers for
 * trading engine and risk manager state.
 */
@Setter
@Getter
@Slf4j
public class SystemOperationsService {

    private static volatile SystemOperationsService instance;

    /**
     * -- GETTER --
     *  Get the SystemActivityBus for activity logging
     */

    private final SystemActivityBus activityBus;

    private final Instant applicationStartTime;

    // Optional data providers - wire these when services are available

    private Supplier<SystemSnapshot.TradingEngineSnapshot> tradingEngineProvider;

    private Supplier<SystemSnapshot.RiskStatusSnapshot> riskStatusProvider;
    private ExchangeService exchangeService;

    private SystemOperationsService() {
        this.activityBus = SystemActivityBus.getInstance();
        this.applicationStartTime = Instant.now();
    }

    /**
     * Get the singleton instance
     */
    public static SystemOperationsService getInstance() {
        if (instance == null) {
            synchronized (SystemOperationsService.class) {
                if (instance == null) {
                    instance = new SystemOperationsService();
                }
            }
        }
        return instance;
    }

    /**
     * Wire the ExchangeService for real-time exchange status monitoring
     */
    public void setExchangeService(@Nullable ExchangeService service) {
        this.exchangeService = service;
        if (service != null) {
            log.info("ExchangeService wired for system monitoring");
        }
    }

    /**
     * Wire the trading engine state provider for real-time trading status
     */
    public void setTradingEngineProvider(@Nullable Supplier<SystemSnapshot.TradingEngineSnapshot> provider) {
        this.tradingEngineProvider = provider;
        if (provider != null) {
            log.info("Trading engine data provider wired");
        }
    }

    /**
     * Wire the risk status provider for real-time risk metrics
     */
    public void setRiskStatusProvider(@Nullable Supplier<SystemSnapshot.RiskStatusSnapshot> provider) {
        this.riskStatusProvider = provider;
        if (provider != null) {
            log.info("Risk status data provider wired");
        }
    }

    /**
     * Create a complete system snapshot
     */
    public SystemSnapshot createSnapshot() {
        SystemSnapshot snapshot = SystemSnapshot.builder()
                .snapshotId(UUID.randomUUID().toString())
                .snapshotTimestamp(Instant.now())
                .appName("InvestPro")
                .appVersion(getApplicationVersion())
                .startupTime(applicationStartTime)
                .uptimeSeconds(calculateUptime())
                .activeProfile(getActiveProfile())
                .javaVersion(System.getProperty("java.version"))
                .javaVendor(System.getProperty("java.vendor"))
                .osName(System.getProperty("os.name"))
                .osVersion(System.getProperty("os.version"))
                .availableProcessors(Runtime.getRuntime().availableProcessors())
                .memoryUsedMb(getMemoryUsedMb())
                .memoryMaxMb(getMemoryMaxMb())
                .memoryPercent(getMemoryPercent())
                .currentTradingMode(getCurrentTradingMode())
                .exchanges(captureExchangeStatus())
                .tradingEngine(captureTradingEngineStatus())
                .riskStatus(captureRiskStatus())
                .errorCount(activityBus.getErrorCount())
                .warningCount(activityBus.getWarningCount())
                .criticalCount(activityBus.getCriticalCount())
                .isHealthy(determineHealthStatus())
                .build();

        log.debug("Created system snapshot: {}", snapshot.getSummary());
        return snapshot;
    }

    /**
     * Capture all exchange statuses from ExchangeService
     */
    private List<SystemSnapshot.ExchangeStatusSnapshot> captureExchangeStatus() {
        List<SystemSnapshot.ExchangeStatusSnapshot> statuses = new ArrayList<>();

        if (exchangeService == null) {
            log.debug("ExchangeService not wired - no exchange status available");
            return statuses;
        }

        try {
            List<String> exchangeNames = exchangeService.getAvailableExchanges();

            for (String name : exchangeNames) {
                try {
                    ExchangeIdentity exchange = exchangeService.getAdapter(name);
                    statuses.add(buildExchangeSnapshot(exchange, exchangeService));
                } catch (Exception e) {
                    log.warn("Error capturing status for exchange: {}", name, e);
                }
            }
        } catch (Exception e) {
            log.warn("Error capturing exchange statuses", e);
        }

        return statuses;
    }

    /**
     * Build snapshot for a single exchange
     */
    private SystemSnapshot.ExchangeStatusSnapshot buildExchangeSnapshot(
            ExchangeIdentity exchange, ExchangeService service) {
        String exchangeName = exchange.getName();

        return SystemSnapshot.ExchangeStatusSnapshot.builder()
                .exchangeName(exchangeName)
                .exchangeId(exchange.getExchangeId())
                .isConnected(getConnectionState(exchange))
                .websocketState(getWebSocketState(exchange))
                .restAvailable(isRestAvailable(service, exchangeName))
                .authStatus(getAuthStatus(service, exchangeName))
                .lastRestRequestTime(null) // TODO: track from exchange adapter
                .lastWebsocketMessageTime(null) // TODO: track from websocket client
                .supportedMarketTypes(getSupportedMarketTypes(exchange))
                .currentTradePair(getCurrentTradePair(exchange))
                .activeSubscriptions(getActiveSubscriptions(exchange))
                .openStreams(getOpenStreams(exchange))
                .lastError(getLastError(exchange))
                .build();
    }

    /**
     * Capture trading engine status from provider or defaults
     */
    private SystemSnapshot.TradingEngineSnapshot captureTradingEngineStatus() {
        if (tradingEngineProvider != null) {
            try {
                return tradingEngineProvider.get();
            } catch (Exception e) {
                log.warn("Error getting trading engine state from provider", e);
            }
        }

        // Return defaults if provider not available
        return SystemSnapshot.TradingEngineSnapshot.builder()
                .botTradingEnabled(false)
                .signalProcessorState("INACTIVE")
                .riskManagerState("INACTIVE")
                .executionEngineState("INACTIVE")
                .strategyEngineState("INACTIVE")
                .activeStrategies(Collections.emptyList())
                .monitoredSymbols(Collections.emptyList())
                .lastSignal(null)
                .lastSignalTime(null)
                .lastApprovedTrade(null)
                .lastApprovedTradeTime(null)
                .lastRejectedTrade(null)
                .lastRejectionReason(null)
                .signalsGeneratedToday(0)
                .tradesApprovedToday(0)
                .tradesRejectedToday(0)
                .build();
    }

    /**
     * Capture risk status from provider or defaults
     */
    private SystemSnapshot.RiskStatusSnapshot captureRiskStatus() {
        if (riskStatusProvider != null) {
            try {
                return riskStatusProvider.get();
            } catch (Exception e) {
                log.warn("Error getting risk status from provider", e);
            }
        }

        // Return defaults if provider not available
        return SystemSnapshot.RiskStatusSnapshot.builder()
                .accountBalance(0.0)
                .riskPerTradePercent(2.0)
                .maxPositionSize(0.0)
                .maxDailyLoss(null)
                .currentDailyLoss(null)
                .portfolioExposurePercent(null)
                .blockedTrades(0)
                .riskWarnings(activityBus.getWarningCount())
                .latestRejectionReason(null)
                .latestRejectionTime(null)
                .build();
    }

    /**
     * Record a system event
     */
    public void recordEvent(SystemActivityEvent.Component component,
            SystemActivityEvent.Severity severity,
            String eventType,
            String message) {
        activityBus.record(component, severity, eventType, message);
    }

    /**
     * Record a system event with correlation ID
     */
    public void recordEvent(SystemActivityEvent.Component component,
            SystemActivityEvent.Severity severity,
            String eventType,
            String message,
            String correlationId) {
        activityBus.record(component, severity, eventType, message, correlationId);
    }

    /**
     * Get recent activity events
     */
    public List<SystemActivityEvent> getRecentEvents(int count) {
        return activityBus.getRecentEvents(count);
    }

    /**
     * Get events by component
     */
    public List<SystemActivityEvent> getEventsByComponent(SystemActivityEvent.Component component) {
        return activityBus.getEventsByComponent(component);
    }

    /**
     * Clear activity history
     */
    public void clearActivityHistory() {
        activityBus.clearHistory();
    }

    // ==================== Exchange State Methods ====================

    private Boolean getConnectionState(ExchangeIdentity exchange) {
        try {
            // Use reflection to call isConnected() if available
            var method = exchange.getClass().getMethod("isConnected");
            Object result = method.invoke(exchange);
            return result instanceof Boolean ? (Boolean) result : false;
        } catch (Exception e) {
            return false; // Default to disconnected if unable to determine
        }
    }

    private String getWebSocketState(ExchangeIdentity exchange) {
        try {
            // Use reflection to call supportsWebSocket() and isWebsocketAvailable()
            var supportsMethod = exchange.getClass().getMethod("supportsWebSocket");
            var availableMethod = exchange.getClass().getMethod("isWebsocketAvailable");

            boolean supports = (Boolean) supportsMethod.invoke(exchange);
            boolean available = (Boolean) availableMethod.invoke(exchange);

            if (!supports)
                return "NOT_SUPPORTED";
            if (available)
                return "CONNECTED";
            return "DISCONNECTED";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private boolean isRestAvailable(ExchangeService service, String exchangeName) {
        try {
            var authResult = service.checkAuthentication(exchangeName);
            return authResult.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    private String getAuthStatus(ExchangeService service, String exchangeName) {
        try {
            var authResult = service.checkAuthentication(exchangeName);
            return authResult.isSuccess() ? "AUTHENTICATED" : "FAILED";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private List<String> getSupportedMarketTypes(ExchangeIdentity exchange) {
        try {
            return exchange.getSupportedMarketTypes().stream()
                    .map(Enum::toString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Nullable
    private String getCurrentTradePair(ExchangeIdentity exchange) {
        try {
            // Use reflection to call getSelectedTradePair() if available
            var method = exchange.getClass().getMethod("getSelectedTradePair");
            Object pair = method.invoke(exchange);
            return pair != null ? pair.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int getActiveSubscriptions(ExchangeIdentity exchange) {
        // TODO: Implement when subscription tracking is available
        return 0;
    }

    private int getOpenStreams(ExchangeIdentity exchange) {
        try {
            // Use reflection to try to get websocket client and stream count
            var wsMethod = exchange.getClass().getMethod("getWebsocketClient");
            Object wsClient = wsMethod.invoke(exchange);
            if (wsClient != null) {
                var streamCountMethod = wsClient.getClass().getMethod("getStreamCount");
                Object count = streamCountMethod.invoke(wsClient);
                if (count instanceof Integer) {
                    return (Integer) count;
                }
            }
        } catch (Exception e) {
            // Silent fail - method may not exist
        }
        return 0;
    }

    @Nullable
    private String getLastError(ExchangeIdentity exchange) {
        // TODO: Implement when error tracking is available in exchange adapters
        return null;
    }

    // ==================== Helper Methods ====================

    private long calculateUptime() {
        if (applicationStartTime == null)
            return 0;
        return ChronoUnit.SECONDS.between(applicationStartTime, Instant.now());
    }

    private long getMemoryUsedMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getMemoryMaxMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    private double getMemoryPercent() {
        return (getMemoryUsedMb() * 100.0) / getMemoryMaxMb();
    }

    private @NotNull String getApplicationVersion() {
        try {
            Package pkg = Package.getPackages()[0];
            if (pkg.getImplementationVersion() != null) {
                return pkg.getImplementationVersion();
            }
        } catch (Exception e) {
            log.debug("Could not determine application version", e);
        }
        return "1.0.0";
    }

    private @NotNull String getActiveProfile() {
        try {
            String profile = System.getProperty("investpro.profile");
            return profile != null ? profile : "default";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getCurrentTradingMode() {
        try {
            String mode = System.getProperty("investpro.trading.mode");
            return mode != null ? mode : "PAPER";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private boolean determineHealthStatus() {
        return activityBus.getCriticalCount() == 0 && activityBus.getErrorCount() < 5;
    }
}
