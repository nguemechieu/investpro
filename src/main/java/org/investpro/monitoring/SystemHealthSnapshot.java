package org.investpro.monitoring;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the entire system's health at a point in time.
 * This is the main output of the system monitor.
 */
@Getter
@Builder
@Setter
public class SystemHealthSnapshot {
    /**
     * Overall system status (aggregate of all components).
     */
    @NotNull
    private final ComponentStatus overallStatus;

    /**
     * Whether the system can trade right now.
     */
    private final boolean canTrade;

    /**
     * High-level summary of system health.
     */
    @NotNull
    private final String summary;

    /**
     * Exchange health (connectivity, type, capabilities).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth exchange = unavailableComponent("Exchange");

    /**
     * Market data health (streaming, selected pair, tickers).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth marketData = unavailableComponent("Market Data");

    /**
     * Account health (balance, positions, permissions).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth account = unavailableComponent("Account");

    /**
     * Strategy health (engine status, last signal, freshness).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth strategy = unavailableComponent("Strategy");

    /**
     * Risk management health (decisions, limits, blockers).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth risk = unavailableComponent("Risk");

    /**
     * Execution health (coordinator, orders, locks).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth execution = unavailableComponent("Execution");

    /**
     * Agent/bot health (registry, automation, events).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth agents = unavailableComponent("Agents");

    /**
     * AI reasoning health (service, last analysis, errors).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth ai = unavailableComponent("AI");

    /**
     * Notification health (Telegram, email, delivery).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth notifications = unavailableComponent("Notifications");

    /**
     * License health (validity, expiration, feature access).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth license = unavailableComponent("License");

    /**
     * System resources health (memory, CPU, disk, threads).
     */
    @NotNull
    @Builder.Default
    private final ComponentHealth systemResources = unavailableComponent("System Resources");

    /**
     * Critical blockers preventing trading.
     */
    @NotNull
    @Builder.Default
    private final List<String> blockers = List.of();

    /**
     * Non-critical warnings that should be addressed.
     */
    @NotNull
    @Builder.Default
    private final List<String> warnings = List.of();

    /**
     * Arbitrary details for debugging or extension.
     */
    @NotNull
    @Builder.Default
    private final Map<String, Object> details = Map.of();

    /**
     * When this snapshot was taken.
     */
    @NotNull
    @Builder.Default
    private final Instant timestamp = Instant.now();

    private static ComponentHealth unavailableComponent(String componentName) {
        return ComponentHealth.builder()
                .componentName(componentName)
                .status(ComponentStatus.UNKNOWN)
                .summary("Not checked")
                .build();
    }

    /**
     * Returns true if system is healthy and can trade.
     */
    public boolean isHealthy() {
        return overallStatus == ComponentStatus.HEALTHY && canTrade;
    }

    /**
     * Returns true if there are critical blockers.
     */
    public boolean hasBlockers() {
        return !blockers.isEmpty();
    }

    /**
     * Returns true if there are warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Returns count of failed/critical components.
     */
    public int getFailedComponentCount() {
        int count = 0;
        if (exchange.isCritical())
            count++;
        if (marketData.isCritical())
            count++;
        if (account.isCritical())
            count++;
        if (strategy.isCritical())
            count++;
        if (risk.isCritical())
            count++;
        if (execution.isCritical())
            count++;
        if (agents.isCritical())
            count++;
        if (ai.isCritical())
            count++;
        if (notifications.isCritical())
            count++;
        if (license.isCritical())
            count++;
        if (systemResources.isCritical())
            count++;
        return count;
    }

    /**
     * Returns count of degraded/warning components.
     */
    public int getDegradedComponentCount() {
        int count = 0;
        if (exchange.hasIssue())
            count++;
        if (marketData.hasIssue())
            count++;
        if (account.hasIssue())
            count++;
        if (strategy.hasIssue())
            count++;
        if (risk.hasIssue())
            count++;
        if (execution.hasIssue())
            count++;
        if (agents.hasIssue())
            count++;
        if (ai.hasIssue())
            count++;
        if (notifications.hasIssue())
            count++;
        if (license.hasIssue())
            count++;
        if (systemResources.hasIssue())
            count++;
        return count;
    }

    /**
     * Returns formatted report as string for display.
     */
    public String toFormattedReport() {
        StringBuilder report = new StringBuilder();

        report.append("═══════════════════════════════════════\n");
        report.append("📊 SYSTEM HEALTH SNAPSHOT\n");
        report.append("═══════════════════════════════════════\n\n");

        report.append("**Overall Status**: ").append(overallStatus.displayName).append("\n");
        report.append("**Can Trade**: ").append(canTrade ? "✅ YES" : "❌ NO").append("\n");
        report.append("**Summary**: ").append(summary).append("\n\n");

        report.append("**Component Status**\n");
        report.append("─────────────────────────\n");
        report.append(exchange.toFormattedString());
        report.append(marketData.toFormattedString());
        report.append(account.toFormattedString());
        report.append(strategy.toFormattedString());
        report.append(risk.toFormattedString());
        report.append(execution.toFormattedString());
        report.append(agents.toFormattedString());
        report.append(ai.toFormattedString());
        report.append(notifications.toFormattedString());
        report.append(license.toFormattedString());
        report.append(systemResources.toFormattedString());

        if (!blockers.isEmpty()) {
            report.append("\n**BLOCKERS**\n");
            report.append("─────────────────────────\n");
            for (String blocker : blockers) {
                report.append("🛑 ").append(blocker).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            report.append("\n**WARNINGS**\n");
            report.append("─────────────────────────\n");
            for (String warning : warnings) {
                report.append("⚠️ ").append(warning).append("\n");
            }
        }

        report.append("\n**Timestamp**: ").append(timestamp).append("\n");

        return report.toString();
    }


}
