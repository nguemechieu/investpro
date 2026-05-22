package org.investpro.activity;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.reconciliation.BrokerReconciliationService;

import java.util.List;

@Slf4j
public class StartupRecoveryService {
    private final List<ExchangeActivityService> activityServices;
    private final BrokerReconciliationService reconciliationService;

    public StartupRecoveryService(List<ExchangeActivityService> activityServices, BrokerReconciliationService reconciliationService) {
        this.activityServices = activityServices;
        this.reconciliationService = reconciliationService;
    }

    public void recover() {
        log.info("StartupRecoveryService: beginning startup sync for {} exchange(s)", activityServices.size());
        for (ExchangeActivityService service : activityServices) {
            try {
                ActivitySyncResult result = service.syncRecentActivity().join();
                log.info("StartupRecovery: sync complete for exchange={} account={} fetched={} processed={} successful={}",
                        result.getExchangeId(), result.getAccountId(), result.getEventsFetched(),
                        result.getEventsProcessed(), result.isSuccessful());
                if (!result.getWarnings().isEmpty()) {
                    result.getWarnings().forEach(w -> log.warn("StartupRecovery warning: {}", w));
                }
            } catch (Exception e) {
                log.error("StartupRecovery: sync failed", e);
            }
        }
        log.info("StartupRecoveryService: startup sync complete");
    }
}
