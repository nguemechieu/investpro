package org.investpro.monitoring;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the entire system's health at a point in time.
 * This is the main output of the system monitor.
 */

@Data
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
    private final ComponentHealth exchange;

    /**
     * Market data health (streaming, selected pair, tickers).
     */
    @NotNull
    private final ComponentHealth marketData;

    /**
     * Account health (balance, positions, permissions).
     */
    @NotNull
    private final ComponentHealth account;

    /**
     * Strategy health (engine status, last signal, freshness).
     */
    @NotNull
    private final ComponentHealth strategy;

    /**
     * Risk management health (decisions, limits, blockers).
     */
    @NotNull
    private final ComponentHealth risk;

    /**
     * Execution health (coordinator, orders, locks).
     */
    @NotNull
    private final ComponentHealth execution;

    /**
     * Agent/bot health (registry, automation, events).
     */
    @NotNull
    private final ComponentHealth agents;

    /**
     * AI reasoning health (service, last analysis, errors).
     */
    @NotNull
    private final ComponentHealth ai;

    /**
     * Notification health (Telegram, email, delivery).
     */
    @NotNull
    private final ComponentHealth notifications;

    /**
     * License health (validity, expiration, feature access).
     */
    @NotNull
    private final ComponentHealth license;

    /**
     * System resources health (memory, CPU, disk, threads).
     */
    @NotNull
    private final ComponentHealth systemResources;

    /**
     * Critical blockers preventing trading.
     */
    @NotNull
    private final List<String> blockers;

    /**
     * Non-critical warnings that should be addressed.
     */
    @NotNull
    private final List<String> warnings;

    /**
     * Arbitrary details for debugging or extension.
     */
    @NotNull
    private final Map<String, Object> details;

    /**
     * When this snapshot was taken.
     */
    @NotNull
    private final Instant timestamp;

    private SystemHealthSnapshot(Builder builder) {
        this.overallStatus = builder.overallStatus;
        this.canTrade = builder.canTrade;
        this.summary = builder.summary;
        this.exchange = builder.exchange == null ? unavailableComponent("Exchange") : builder.exchange;
        this.marketData = builder.marketData == null ? unavailableComponent("Market Data") : builder.marketData;
        this.account = builder.account == null ? unavailableComponent("Account") : builder.account;
        this.strategy = builder.strategy == null ? unavailableComponent("Strategy") : builder.strategy;
        this.risk = builder.risk == null ? unavailableComponent("Risk") : builder.risk;
        this.execution = builder.execution == null ? unavailableComponent("Execution") : builder.execution;
        this.agents = builder.agents == null ? unavailableComponent("Agents") : builder.agents;
        this.ai = builder.ai == null ? unavailableComponent("AI") : builder.ai;
        this.notifications = builder.notifications == null ? unavailableComponent("Notifications")
                : builder.notifications;
        this.license = builder.license == null ? unavailableComponent("License") : builder.license;
        this.systemResources = builder.systemResources == null ? unavailableComponent("System Resources")
                : builder.systemResources;
        this.blockers = builder.blockers == null ? List.of() : List.copyOf(builder.blockers);
        this.warnings = builder.warnings == null ? List.of() : List.copyOf(builder.warnings);
        this.details = builder.details == null ? Map.of() : Map.copyOf(builder.details);
        this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static ComponentHealth unavailableComponent(String componentName) {
        return ComponentHealth.builder()
                .componentName(componentName)
                .status(ComponentStatus.UNKNOWN)
                .summary("Not checked")
                .build();
    }

    public static final class Builder {
        private ComponentStatus overallStatus;
        private boolean canTrade;
        private String summary;
        private ComponentHealth exchange;
        private ComponentHealth marketData;
        private ComponentHealth account;
        private ComponentHealth strategy;
        private ComponentHealth risk;
        private ComponentHealth execution;
        private ComponentHealth agents;
        private ComponentHealth ai;
        private ComponentHealth notifications;
        private ComponentHealth license;
        private ComponentHealth systemResources;
        private List<String> blockers;
        private List<String> warnings;
        private Map<String, Object> details;
        private Instant timestamp;

        public Builder overallStatus(ComponentStatus overallStatus) {
            this.overallStatus = overallStatus;
            return this;
        }

        public Builder canTrade(boolean canTrade) {
            this.canTrade = canTrade;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder exchange(ComponentHealth exchange) {
            this.exchange = exchange;
            return this;
        }

        public Builder marketData(ComponentHealth marketData) {
            this.marketData = marketData;
            return this;
        }

        public Builder account(ComponentHealth account) {
            this.account = account;
            return this;
        }

        public Builder strategy(ComponentHealth strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder risk(ComponentHealth risk) {
            this.risk = risk;
            return this;
        }

        public Builder execution(ComponentHealth execution) {
            this.execution = execution;
            return this;
        }

        public Builder agents(ComponentHealth agents) {
            this.agents = agents;
            return this;
        }

        public Builder ai(ComponentHealth ai) {
            this.ai = ai;
            return this;
        }

        public Builder notifications(ComponentHealth notifications) {
            this.notifications = notifications;
            return this;
        }

        public Builder license(ComponentHealth license) {
            this.license = license;
            return this;
        }

        public Builder systemResources(ComponentHealth systemResources) {
            this.systemResources = systemResources;
            return this;
        }

        public Builder blockers(List<String> blockers) {
            this.blockers = blockers;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public SystemHealthSnapshot build() {
            return new SystemHealthSnapshot(this);
        }
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
