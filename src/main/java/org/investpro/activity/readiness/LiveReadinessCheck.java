package org.investpro.activity.readiness;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LiveReadinessCheck {
    String name;
    LiveReadinessStatus status;
    String detail;

    public boolean isPassing() {
        return status == LiveReadinessStatus.READY;
    }
}
