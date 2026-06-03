package org.investpro.monitoring;

import lombok.Builder;
import lombok.Data;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Health information for a single component.
 */
@Slf4j
@Data
@Builder
@ToString
public class ComponentHealth {
    @NotNull
    private final String componentName;

    @NotNull
    private final ComponentStatus status;

    @NotNull
    private final String summary;

    @Nullable
    private final String issue;

    @Nullable
    private final String recommendedAction;

    @NotNull
    @Builder.Default
    private final Instant lastCheckedAt = Instant.now();

    @Nullable
    private final Instant lastSuccessAt;

    @Nullable
    private final Instant lastFailureAt;

    @NotNull
    @Builder.Default
    private final List<String> warnings = List.of();

    @NotNull
    @Builder.Default
    private final List<String> blockers = List.of();

    @NotNull
    @Builder.Default
    private final Map<String, Object> details = Map.of();

    /**
     * Returns true if this component is operational (HEALTHY or DEGRADED).
     */
    public boolean isOperational() {
        return status.isOperational();
    }

    /**
     * Returns true if this component has an issue (WARNING or worse).
     */
    public boolean hasIssue() {
        return status.hasIssue();
    }

    /**
     * Returns true if this component has any blockers.
     */
    public boolean hasBlockers() {
        return !blockers.isEmpty();
    }

    /**
     * Returns true if this component has any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Returns true if component is critical (FAILED status).
     */
    public boolean isCritical() {
        return status.isCritical();
    }

    /**
     * Returns a formatted string representing this component's health.
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(componentName).append("**: ").append(status.displayName).append("\n");
        sb.append("Summary: ").append(summary).append("\n");

        if (issue != null && !issue.isBlank()) {
            sb.append("Issue: ").append(issue).append("\n");
        }

        if (!blockers.isEmpty()) {
            sb.append("Blockers: ").append(String.join(", ", blockers)).append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("Warnings: ").append(String.join(", ", warnings)).append("\n");
        }

        if (recommendedAction != null && !recommendedAction.isBlank()) {
            sb.append("Recommended: ").append(recommendedAction).append("\n");
        }

        return sb.toString();
    }
}
