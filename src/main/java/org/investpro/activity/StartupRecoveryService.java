package org.investpro.activity;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.reconciliation.BrokerReconciliationService;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs on application startup for each configured exchange/account:
 * 1. Load last checkpoint cursor
 * 2. Sync activity from cursor
 * 3. Project events
 * 4. Reconcile
 */
@Slf4j
public class StartupRecoveryService {

    public record ExchangeAccount(String exchangeId, String accountId) {}

    private final List<ExchangeActivityService> activityServices;
    private final BrokerReconciliationService reconciliationService;
    private final List<ExchangeAccount> accounts;

    public StartupRecoveryService(
            List<ExchangeActivityService> activityServices,
            BrokerReconciliationService reconciliationService,
            List<ExchangeAccount> accounts
    ) {
        this.activityServices = new ArrayList<>(activityServices == null ? List.of() : activityServices);
        this.reconciliationService = reconciliationService;
        this.accounts = new ArrayList<>(accounts == null ? List.of() : accounts);
    }

    public void recover() {
        log.info("StartupRecoveryService: beginning startup recovery for {} exchange/account pairs",
                activityServices.size());

        for (ExchangeActivityService service : activityServices) {
            try {
                ActivitySyncResult result = service.syncRecentActivity().join();
                if (result.isSuccessful()) {
                    log.info("Startup sync complete for {}/{}: {} events fetched, {} processed",
                            result.getExchangeId(), result.getAccountId(),
                            result.getEventsFetched(), result.getEventsProcessed());
                } else {
                    log.warn("Startup sync partial/failed for {}/{}: errors={}",
                            result.getExchangeId(), result.getAccountId(), result.getErrors());
                }
            } catch (Exception e) {
                log.error("Startup sync threw exception", e);
            }
        }

        // Run initial reconciliation for each account
        for (ExchangeAccount account : accounts) {
            try {
                var result = reconciliationService
                        .reconcileExchangeAccount(account.exchangeId(), account.accountId())
                        .join();
                if (!result.isClean()) {
                    log.warn("Startup reconciliation found {} mismatches for {}/{}",
                            result.getMismatchCount(), account.exchangeId(), account.accountId());
                } else {
                    log.info("Startup reconciliation clean for {}/{}",
                            account.exchangeId(), account.accountId());
                }
            } catch (Exception e) {
                log.error("Startup reconciliation failed for {}/{}",
                        account.exchangeId(), account.accountId(), e);
            }
        }

        log.info("StartupRecoveryService: startup recovery complete");
    }
}
