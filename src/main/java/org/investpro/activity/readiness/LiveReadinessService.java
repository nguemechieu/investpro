package org.investpro.activity.readiness;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.ActivityCheckpointRepository;
import org.investpro.activity.BrokerActivityRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class LiveReadinessService {
    private final BrokerActivityRepository activityRepository;
    private final ActivityCheckpointRepository checkpointRepository;
    private final String exchangeId;
    private final String accountId;
    private final long maxStalenessMinutes;

    public LiveReadinessService(
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            String exchangeId,
            String accountId,
            long maxStalenessMinutes
    ) {
        this.activityRepository = activityRepository;
        this.checkpointRepository = checkpointRepository;
        this.exchangeId = exchangeId;
        this.accountId = accountId;
        this.maxStalenessMinutes = maxStalenessMinutes;
    }

    public LiveReadinessReport assess() {
        List<LiveReadinessCheck> checks = new ArrayList<>();
        checks.add(checkSyncFreshness());
        checks.add(checkUnprojectedBacklog());
        return LiveReadinessReport.builder().checks(checks).build();
    }

    private LiveReadinessCheck checkSyncFreshness() {
        Optional<Instant> lastSync = checkpointRepository.getLastSyncTime(exchangeId, accountId);
        if (lastSync.isEmpty()) {
            return LiveReadinessCheck.builder()
                    .name("sync-freshness")
                    .status(LiveReadinessStatus.NOT_READY)
                    .detail("No sync recorded for " + exchangeId + ":" + accountId)
                    .build();
        }
        long minutesAgo = ChronoUnit.MINUTES.between(lastSync.get(), Instant.now());
        if (minutesAgo > maxStalenessMinutes) {
            return LiveReadinessCheck.builder()
                    .name("sync-freshness")
                    .status(LiveReadinessStatus.DEGRADED)
                    .detail("Last sync was " + minutesAgo + "m ago (threshold=" + maxStalenessMinutes + "m)")
                    .build();
        }
        return LiveReadinessCheck.builder()
                .name("sync-freshness")
                .status(LiveReadinessStatus.READY)
                .detail("Last sync " + minutesAgo + "m ago")
                .build();
    }

    private LiveReadinessCheck checkUnprojectedBacklog() {
        List<?> backlog = activityRepository.findUnprojectedEvents(exchangeId, accountId, 10);
        if (backlog.size() >= 10) {
            return LiveReadinessCheck.builder()
                    .name("unprojected-backlog")
                    .status(LiveReadinessStatus.DEGRADED)
                    .detail("10+ unprojected events pending for " + exchangeId + ":" + accountId)
                    .build();
        }
        return LiveReadinessCheck.builder()
                .name("unprojected-backlog")
                .status(LiveReadinessStatus.READY)
                .detail("Unprojected backlog: " + backlog.size())
                .build();
    }
}
