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
    @Builder.Default Instant startedAt = Instant.now();
    @Builder.Default Instant finishedAt = Instant.now();
    @Singular List<ReconciliationMismatch> mismatches;
    int repaired;
    boolean successful;
    String error;

    public int getMismatchCount() {
        return mismatches == null ? 0 : mismatches.size();
    }

    public boolean isClean() {
        return successful && getMismatchCount() == 0;
    }
}
