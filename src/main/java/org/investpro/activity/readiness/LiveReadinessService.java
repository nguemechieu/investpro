package org.investpro.activity.readiness;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LiveReadinessService {

    private final List<LiveReadinessCheck> checks;

    public LiveReadinessService(List<LiveReadinessCheck> checks) {
        this.checks = checks == null ? List.of() : checks;
    }

    public LiveReadinessReport assess() {
        Map<String, LiveReadinessStatus> results = new LinkedHashMap<>();
        LiveReadinessStatus overall = LiveReadinessStatus.READY;

        for (LiveReadinessCheck check : checks) {
            LiveReadinessStatus status;
            try {
                status = check.probe().get();
                if (status == null) status = LiveReadinessStatus.UNKNOWN;
            } catch (Exception e) {
                log.warn("LiveReadinessService: check '{}' threw exception: {}", check.name(), e.getMessage());
                status = LiveReadinessStatus.NOT_READY;
            }
            results.put(check.name(), status);

            if (status == LiveReadinessStatus.NOT_READY) {
                overall = LiveReadinessStatus.NOT_READY;
            } else if (status == LiveReadinessStatus.DEGRADED && overall != LiveReadinessStatus.NOT_READY) {
                overall = LiveReadinessStatus.DEGRADED;
            } else if (status == LiveReadinessStatus.UNKNOWN && overall == LiveReadinessStatus.READY) {
                overall = LiveReadinessStatus.UNKNOWN;
            }
        }

        return LiveReadinessReport.builder()
                .checkedAt(Instant.now())
                .overallStatus(overall)
                .checkResults(results)
                .build();
    }
}
