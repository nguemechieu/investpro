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
    Instant reconciledAt;
    int eventsChecked;
    boolean successful;
    @Singular List<ReconciliationMismatch> mismatches;

    public boolean isClean() { return successful && getMismatchCount() == 0; }
    public int getMismatchCount() { return mismatches == null ? 0 : mismatches.size(); }
}
