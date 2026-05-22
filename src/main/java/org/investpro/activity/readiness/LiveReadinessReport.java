package org.investpro.activity.readiness;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class LiveReadinessReport {
    Instant checkedAt;
    LiveReadinessStatus overallStatus;
    @Singular Map<String, LiveReadinessStatus> checkResults;

    public boolean isReady() { return overallStatus == LiveReadinessStatus.READY; }
}
