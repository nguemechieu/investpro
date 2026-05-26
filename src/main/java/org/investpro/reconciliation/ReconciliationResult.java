package org.investpro.reconciliation;

import java.time.Instant;
import java.util.List;

public record ReconciliationResult(
        ReconciliationStatus status,
        boolean liveExecutionBlocked,
        List<String> mismatches,
        List<String> repairs,
        Instant checkedAt) {

    public ReconciliationResult {
        status = status == null ? ReconciliationStatus.UNKNOWN : status;
        mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
        repairs = repairs == null ? List.of() : List.copyOf(repairs);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }
}
