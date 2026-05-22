package org.investpro.activity.readiness;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LiveReadinessReport {
    String exchangeId;
    String accountId;
    @Singular List<LiveReadinessCheck> checks;

    public boolean isReadyToTrade() {
        return checks != null && checks.stream()
                .noneMatch(c -> c.getStatus() == LiveReadinessStatus.FAIL && c.isCritical());
    }

    public long criticalFailures() {
        return checks == null ? 0 : checks.stream()
                .filter(c -> c.getStatus() == LiveReadinessStatus.FAIL && c.isCritical())
                .count();
    }

    public long warnings() {
        return checks == null ? 0 : checks.stream()
                .filter(c -> c.getStatus() == LiveReadinessStatus.WARN)
                .count();
    }
}
