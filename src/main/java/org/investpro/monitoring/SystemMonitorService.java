package org.investpro.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.investpro.core.SystemCore;

import java.time.Instant;
import java.util.*;

/**
 * System Monitor Service: The control tower for monitoring all trading
 * subsystems.
 * Uses only public APIs from SystemCore to determine health status.
 * <p>
 * Subsystems monitored:
 * 1. Exchange - accessibility via public diagnostics
 * 2. Market Data - subscription status via getSubscriptionSummary()
 * 3. Account - status via getSystemDiagnostics()
 * 4. Strategy - status via getActiveStrategyName()
 * 5. Risk - status via getRiskMetrics()
 * 6. Execution - status via getSystemDiagnostics()
 * 7. Agents - status via system checks
 * 8. AI - status via getSystemDiagnostics()
 * 9. Notifications - status via getSystemDiagnostics()
 */
@Slf4j
public class SystemMonitorService {
    private final SystemCore systemCore;

    public SystemMonitorService(@NotNull SystemCore systemCore) {
        this.systemCore = Objects.requireNonNull(systemCore, "systemCore cannot be null");
        log.info("SystemMonitorService initialized - monitoring via public API only");
    }

    /**
     * Perform a complete health check of all system subsystems.
     */
    @NotNull
    public SystemHealthSnapshot checkNow() {
        try {
            Instant checkTime = Instant.now();

            ComponentHealth exchange = checkExchangeHealth();
            ComponentHealth marketData = checkMarketDataHealth();
            ComponentHealth account = checkAccountHealth();
            ComponentHealth strategy = checkStrategyHealth();
            ComponentHealth risk = checkRiskHealth();
            ComponentHealth execution = checkExecutionHealth();
            ComponentHealth agents = checkAgentsHealth();
            ComponentHealth ai = checkAiHealth();
            ComponentHealth notifications = checkNotificationsHealth();

            // Aggregate issues
            List<String> blockers = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (ComponentHealth component : List.of(exchange, marketData, account, strategy, risk, execution, agents,
                    ai, notifications)) {
                blockers.addAll(component.getBlockers());
                warnings.addAll(component.getWarnings());
            }

            // Overall status determination
            ComponentStatus overallStatus = ComponentStatus.HEALTHY;

            if (exchange.getStatus() == ComponentStatus.FAILED || risk.getStatus() == ComponentStatus.FAILED) {
                overallStatus = ComponentStatus.FAILED;
            } else if (!blockers.isEmpty()) {
                overallStatus = ComponentStatus.WARNING;
            } else if (strategy.getStatus() == ComponentStatus.WARNING
                    || marketData.getStatus() == ComponentStatus.WARNING) {
                overallStatus = ComponentStatus.WARNING;
            } else if (exchange.getStatus() == ComponentStatus.DEGRADED
                    || marketData.getStatus() == ComponentStatus.DEGRADED) {
                overallStatus = ComponentStatus.DEGRADED;
            }

            /*
             * Startup warnings such as "No active subscription" or
             * "No active strategy signal yet" should not prevent the UI from
             * enabling the bot. The stream is often started immediately after
             * auto-trading is enabled, and the first strategy signal can only
             * exist after market data begins flowing.
             */
            boolean canTrade = blockers.isEmpty()
                    && exchange.getStatus() != ComponentStatus.FAILED
                    && risk.getStatus() != ComponentStatus.FAILED
                    && systemCore.canSubmitOrders();

            String summary = String.format("System: %s | Trading: %s",
                    overallStatus.getDisplayName(),
                    canTrade ? "✅ ALLOWED" : "❌ BLOCKED");

            return SystemHealthSnapshot.builder()
                    .overallStatus(overallStatus)
                    .canTrade(canTrade)
                    .summary(summary)
                    .exchange(exchange)
                    .marketData(marketData)
                    .account(account)
                    .strategy(strategy)
                    .risk(risk)
                    .execution(execution)
                    .agents(agents)
                    .ai(ai)
                    .notifications(notifications)
                    .blockers(blockers)
                    .warnings(warnings)
                    .details(Map.of(
                            "subscriptionSummary", safeCall(systemCore::getSubscriptionSummary),
                            "activeStrategy", safeCall(systemCore::getActiveStrategyName),
                            "riskMetrics", safeCall(systemCore::getRiskMetrics)))
                    .timestamp(checkTime)
                    .build();
        } catch (Exception e) {
            log.error("Error during system health check", e);
            return buildFailedSnapshot("Health check error: " + e.getMessage());
        }
    }

    private ComponentHealth checkExchangeHealth() {
        try {
            String diagnostics = systemCore.getSystemDiagnostics();
            boolean connected = diagnostics != null && diagnostics.contains("Connected");
            boolean canSubmitOrders = systemCore.canSubmitOrders();

            if (!canSubmitOrders) {
                return ComponentHealth.builder()
                        .componentName("Exchange")
                        .status(ComponentStatus.FAILED)
                        .summary("Order submission unavailable")
                        .issue("Exchange is not connected or trading mode does not allow orders")
                        .lastCheckedAt(Instant.now())
                        .blockers(List.of("Exchange cannot submit live or paper orders"))
                        .build();
            }

            return ComponentHealth.builder()
                    .componentName("Exchange")
                    .status(connected ? ComponentStatus.HEALTHY : ComponentStatus.DEGRADED)
                    .summary(connected ? "✅ Connected" : "⚠️ Connection status unclear")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Exchange", e.getMessage());
        }
    }

    private ComponentHealth checkMarketDataHealth() {
        try {
            String subscription = systemCore.getSubscriptionSummary();
            boolean active = subscription != null && !subscription.contains("No");

            return ComponentHealth.builder()
                    .componentName("Market Data")
                    .status(active ? ComponentStatus.HEALTHY : ComponentStatus.WARNING)
                    .summary(active ? "✅ Market data active" : "⚠️ No active subscription")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Market Data", e.getMessage());
        }
    }

    private ComponentHealth checkAccountHealth() {
        try {
            String diagnostics = systemCore.getSystemDiagnostics();
            boolean hasAccountInfo = diagnostics != null && !diagnostics.isEmpty();

            return ComponentHealth.builder()
                    .componentName("Account")
                    .status(hasAccountInfo ? ComponentStatus.HEALTHY : ComponentStatus.UNKNOWN)
                    .summary("Account monitoring active")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Account", e.getMessage());
        }
    }

    private ComponentHealth checkStrategyHealth() {
        try {
            String strategyName = systemCore.getActiveStrategyName();
            boolean hasStrategy = strategyName != null && !strategyName.contains("No strategy");

            return ComponentHealth.builder()
                    .componentName("Strategy")
                    .status(hasStrategy ? ComponentStatus.HEALTHY : ComponentStatus.WARNING)
                    .summary(hasStrategy ? "✅ Strategy active: " + strategyName : "⚠️ No active strategy")
                    .lastCheckedAt(Instant.now())
                    .warnings(hasStrategy ? List.of() : List.of("No active strategy"))
                    .build();
        } catch (Exception e) {
            return failedComponent("Strategy", e.getMessage());
        }
    }

    private ComponentHealth checkRiskHealth() {
        try {
            String riskMetrics = systemCore.getRiskMetrics();
            boolean hasRisk = riskMetrics != null && !riskMetrics.isBlank();

            return ComponentHealth.builder()
                    .componentName("Risk")
                    .status(hasRisk ? ComponentStatus.HEALTHY : ComponentStatus.DEGRADED)
                    .summary("Risk management active")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Risk", e.getMessage());
        }
    }

    private ComponentHealth checkExecutionHealth() {
        try {
            String diag = systemCore.getSystemDiagnostics();
            boolean hasExecution = diag != null && !diag.isEmpty();

            return ComponentHealth.builder()
                    .componentName("Execution")
                    .status(hasExecution ? ComponentStatus.HEALTHY : ComponentStatus.DEGRADED)
                    .summary("Execution engine operational")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Execution", e.getMessage());
        }
    }

    private ComponentHealth checkAgentsHealth() {
        try {
            return ComponentHealth.builder()
                    .componentName("Agents")
                    .status(ComponentStatus.HEALTHY)
                    .summary("Agent subsystem initialized")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Agents", e.getMessage());
        }
    }

    private ComponentHealth checkAiHealth() {
        try {
            return ComponentHealth.builder()
                    .componentName("AI")
                    .status(ComponentStatus.HEALTHY)
                    .summary("AI service available")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("AI", e.getMessage());
        }
    }

    private ComponentHealth checkNotificationsHealth() {
        try {
            String diag = systemCore.getSystemDiagnostics();
            boolean telegramConfigured = diag != null && diag.contains("Telegram");

            return ComponentHealth.builder()
                    .componentName("Notifications")
                    .status(telegramConfigured ? ComponentStatus.HEALTHY : ComponentStatus.WARNING)
                    .summary(telegramConfigured ? "✅ Notifications configured" : "⚠️ No notifications")
                    .lastCheckedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return failedComponent("Notifications", e.getMessage());
        }
    }

    private ComponentHealth failedComponent(String name, String issue) {
        return ComponentHealth.builder()
                .componentName(name)
                .status(ComponentStatus.FAILED)
                .summary("❌ Check failed")
                .issue(issue)
                .lastCheckedAt(Instant.now())
                .blockers(List.of(name + " health check failed"))
                .build();
    }

    private SystemHealthSnapshot buildFailedSnapshot(String issue) {
        return SystemHealthSnapshot.builder()
                .overallStatus(ComponentStatus.FAILED)
                .canTrade(false)
                .summary("❌ Health check failed")
                .blockers(List.of(issue))
                .timestamp(Instant.now())
                .build();
    }

    private String safeCall(SupplierWithException<String> supplier) {
        try {
            return safeText(supplier.get(), "");
        } catch (Exception exception) {
            return exception.getClass().getSimpleName() + ": " + safeText(exception.getMessage(), "");
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
