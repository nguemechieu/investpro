package org.investpro.strategy.user;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Metadata descriptor for a loaded user strategy.
 *
 * Used for UI display and status tracking throughout the strategy lifecycle.
 */
@Getter
@Builder(toBuilder = true)
public class UserStrategyDescriptor {

    /**
     * Unique strategy identifier.
     */
    private final String id;

    /**
     * Human-readable strategy name.
     */
    private final String name;

    /**
     * Strategy description/purpose.
     */
    private final String description;

    /**
     * JAR file path where this strategy was loaded from.
     */
    private final String sourceJar;

    /**
     * When this strategy was loaded (epoch milliseconds).
     */
    private final long loadedAtEpochMs;

    /**
     * Current lifecycle status.
     */
    @Builder.Default
    private final UserStrategyStatus status = UserStrategyStatus.DISCOVERED;

    /**
     * Whether the strategy has passed validation.
     */
    private final boolean validated;

    /**
     * Validation error/warning message if validation failed.
     */
    @Nullable
    private final String validationMessage;

    /**
     * Number of candles required for warmup.
     */
    private final int warmupBars;

    /**
     * Optional detailed validation result.
     */
    @Nullable
    private final UserStrategyValidationResult validationResult;

    /**
     * Notes or additional information.
     */
    @Nullable
    private final String notes;

    // =========================================================================
    // Convenience methods
    // =========================================================================

    public boolean isReady() {
        return status.isReady();
    }

    public boolean isFailed() {
        return status.isFailure();
    }

    public boolean isInProgress() {
        return status.isInProgress();
    }

    public String getStatusDisplay() {
        return status.name().replace('_', ' ');
    }

    /**
     * Create a descriptor for a newly discovered strategy.
     */
    public static UserStrategyDescriptor discovered(String id, String name, String sourceJar) {
        return UserStrategyDescriptor.builder()
                .id(id)
                .name(name)
                .sourceJar(sourceJar)
                .loadedAtEpochMs(System.currentTimeMillis())
                .status(UserStrategyStatus.DISCOVERED)
                .validated(false)
                .warmupBars(100)
                .build();
    }

    /**
     * Create a descriptor for a loaded strategy.
     */
    public static UserStrategyDescriptor loaded(
            String id,
            String name,
            String description,
            String sourceJar,
            int warmupBars) {
        return UserStrategyDescriptor.builder()
                .id(id)
                .name(name)
                .description(description)
                .sourceJar(sourceJar)
                .loadedAtEpochMs(System.currentTimeMillis())
                .status(UserStrategyStatus.LOADED)
                .validated(false)
                .warmupBars(warmupBars)
                .build();
    }
}
