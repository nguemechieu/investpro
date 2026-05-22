package org.investpro.activity.reconciliation;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class ReconciliationResult {
    String exchangeId;
    String accountId;
    @Builder.Default Instant reconciledAt = Instant.now();
    @Singular List<ReconciliationMismatch> mismatches;
    int eventsChecked;

    public boolean isClean() {
        return mismatches == null || mismatches.isEmpty();
    }

    public int getMismatchCount() {
        return mismatches == null ? 0 : mismatches.size();
    }
}
