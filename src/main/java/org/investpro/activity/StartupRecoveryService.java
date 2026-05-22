package org.investpro.activity;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class StartupRecoveryService {
    private final List<ExchangeActivityService> activityServices;

    public StartupRecoveryService(List<ExchangeActivityService> activityServices) {
        this.activityServices = activityServices == null ? List.of() : List.copyOf(activityServices);
    }

    public CompletableFuture<Void> recoverAll() {
        if (activityServices.isEmpty()) {
            log.info("StartupRecoveryService: no activity services registered");
            return CompletableFuture.completedFuture(null);
        }
        log.info("StartupRecoveryService: recovering activity for {} service(s)", activityServices.size());
        List<CompletableFuture<ActivitySyncResult>> futures = activityServices.stream()
                .map(svc -> svc.syncRecentActivity()
                        .thenApply(result -> {
                            if (result.isSuccessful()) {
                                log.info("Recovery OK: {}:{} — fetched={} processed={}",
                                        result.getExchangeId(), result.getAccountId(),
                                        result.getEventsFetched(), result.getEventsProcessed());
                            } else {
                                log.warn("Recovery issues: {}:{} — warnings={} errors={}",
                                        result.getExchangeId(), result.getAccountId(),
                                        result.getWarnings(), result.getErrors());
                            }
                            return result;
                        })
                        .exceptionally(ex -> {
                            log.error("Recovery error for service {}: {}", svc.getClass().getSimpleName(), ex.getMessage());
                            return null;
                        }))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
