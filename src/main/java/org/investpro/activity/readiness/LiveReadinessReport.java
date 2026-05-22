package org.investpro.activity.readiness;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class LiveReadinessReport {
    @Builder.Default Instant assessedAt = Instant.now();
    @Singular List<LiveReadinessCheck> checks;

    public boolean isReady() {
        return checks != null && checks.stream().allMatch(LiveReadinessCheck::isPassing);
    }

    public LiveReadinessStatus overallStatus() {
        if (checks == null || checks.isEmpty()) return LiveReadinessStatus.NOT_READY;
        if (isReady()) return LiveReadinessStatus.READY;
        boolean anyReady = checks.stream().anyMatch(c -> c.getStatus() == LiveReadinessStatus.READY);
        return anyReady ? LiveReadinessStatus.DEGRADED : LiveReadinessStatus.NOT_READY;
    }
}
