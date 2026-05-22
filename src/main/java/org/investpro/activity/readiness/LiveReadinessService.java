package org.investpro.activity.readiness;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.ActivityCheckpointRepository;
import org.investpro.activity.BrokerActivityRepository;

/**
 * Runs the live-readiness pre-flight checklist.
 * Callers should run this before enabling bot trading.
 */
@Slf4j
public class LiveReadinessService {

    private final BrokerActivityRepository activityRepository;
    private final ActivityCheckpointRepository checkpointRepository;
    private final boolean blockOnCriticalFailure;

    public LiveReadinessService(
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            boolean blockOnCriticalFailure
    ) {
        this.activityRepository = activityRepository;
        this.checkpointRepository = checkpointRepository;
        this.blockOnCriticalFailure = blockOnCriticalFailure;
    }

    public LiveReadinessReport evaluate(String exchangeId, String accountId) {
        LiveReadinessReport.LiveReadinessReportBuilder report = LiveReadinessReport.builder()
                .exchangeId(exchangeId)
                .accountId(accountId);

        // 1. Repository accessible
        report.check(checkRepositoryHealth());

        // 2. Cursor / initial sync status
        report.check(checkCursorPresent(exchangeId, accountId));

        // 3. Last sync not too old (within 5 minutes)
        report.check(checkSyncFreshness(exchangeId, accountId));

        // 4. No projection failures pending
        report.check(checkUnprojectedEvents(exchangeId, accountId));

        LiveReadinessReport result = report.build();
        if (blockOnCriticalFailure && !result.isReadyToTrade()) {
            log.warn("LiveReadiness BLOCKED for {}/{}: {} critical failures",
                    exchangeId, accountId, result.criticalFailures());
        } else if (result.warnings() > 0) {
            log.info("LiveReadiness for {}/{}: {} warnings", exchangeId, accountId, result.warnings());
        } else {
            log.info("LiveReadiness PASS for {}/{}", exchangeId, accountId);
        }
        return result;
    }

    private LiveReadinessCheck checkRepositoryHealth() {
        try {
            // A lightweight query to confirm repository is reachable
            activityRepository.exists("__health_check__", "__none__");
            return LiveReadinessCheck.pass("broker-activity-store");
        } catch (Exception e) {
            return LiveReadinessCheck.fail("broker-activity-store",
                    "Broker activity store is not accessible: " + e.getMessage(),
                    "Check database configuration and file permissions",
                    true);
        }
    }

    private LiveReadinessCheck checkCursorPresent(String exchangeId, String accountId) {
        boolean hasCursor = checkpointRepository.getLastCursor(exchangeId, accountId).isPresent();
        if (hasCursor) return LiveReadinessCheck.pass("cursor-present");
        return LiveReadinessCheck.warn("cursor-present",
                "No cursor found for " + exchangeId + "/" + accountId + "; initial sync required",
                "Trigger activity sync to establish baseline cursor");
    }

    private LiveReadinessCheck checkSyncFreshness(String exchangeId, String accountId) {
        var lastSync = checkpointRepository.getLastSyncTime(exchangeId, accountId);
        if (lastSync.isEmpty()) {
            return LiveReadinessCheck.warn("sync-freshness",
                    "Activity sync has never completed for " + exchangeId,
                    "Run initial activity sync");
        }
        long ageSeconds = java.time.Duration.between(lastSync.get(), java.time.Instant.now()).getSeconds();
        if (ageSeconds > 120) {
            return LiveReadinessCheck.warn("sync-freshness",
                    "Last activity sync was " + ageSeconds + "s ago (>120s)",
                    "Check activity sync scheduler");
        }
        return LiveReadinessCheck.pass("sync-freshness");
    }

    private LiveReadinessCheck checkUnprojectedEvents(String exchangeId, String accountId) {
        int count = activityRepository.findUnprojectedEvents(exchangeId, accountId, 10).size();
        if (count == 0) return LiveReadinessCheck.pass("projection-complete");
        if (count < 10) {
            return LiveReadinessCheck.warn("projection-complete",
                    count + " unprojected broker events pending",
                    "Wait for projection engine to catch up");
        }
        return LiveReadinessCheck.fail("projection-complete",
                "10+ unprojected broker events — projection engine may be stuck",
                "Check projection error logs and restart activity service",
                true);
    }
}
