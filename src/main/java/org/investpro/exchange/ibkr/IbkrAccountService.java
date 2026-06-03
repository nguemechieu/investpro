package org.investpro.exchange.ibkr;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class IbkrAccountService {

    private final IbkrConnectionManager connectionManager;
    private final IbkrPersistenceStore persistenceStore;
    private final IbkrClientPortalClient clientPortalClient;
    private final boolean paper;
    private final AtomicReference<IbkrAccountSnapshot> snapshotRef;

    public IbkrAccountService(IbkrConnectionManager connectionManager, IbkrPersistenceStore persistenceStore,
            IbkrClientPortalClient clientPortalClient,
            boolean paper) {
        this.connectionManager = connectionManager;
        this.persistenceStore = persistenceStore;
        this.clientPortalClient = clientPortalClient;
        this.paper = paper;
        this.snapshotRef = new AtomicReference<>(
                IbkrAccountSnapshot.paperDefault(paper ? "DU-PAPER" : "U-LIVE"));
    }

    public IbkrAccountSnapshot snapshot() {
        return snapshotRef.get();
    }

    public IbkrAccountSnapshot refreshFromBrokerIfAvailable() {
        if (paper || clientPortalClient == null || !connectionManager.isConnected()) {
            return snapshot();
        }

        clientPortalClient.fetchAccountSnapshot(false)
                .ifPresent(snapshot -> {
                    snapshotRef.set(snapshot);
                    persistenceStore.persistAccount(snapshot);
                });
        return snapshot();
    }

    public void setBalance(String currency, double total, double available) {
        IbkrAccountSnapshot current = snapshotRef.get();
        Map<String, Double> balances = new LinkedHashMap<>(current.balances());
        balances.put(currency.toUpperCase(), Math.max(0.0, total));

        IbkrAccountSnapshot updated = new IbkrAccountSnapshot(
                current.accountId(),
                current.broker(),
                current.paper(),
                Math.max(current.equity(), total),
                Math.max(0.0, available),
                current.marginUsed(),
                current.buyingPower(),
                balances,
                Instant.now());

        snapshotRef.set(updated);
        persistenceStore.persistAccount(updated);
    }

    public void applyFillCashChange(double delta) {
        IbkrAccountSnapshot current = snapshotRef.get();
        double usd = current.balances().getOrDefault("USD", 0.0) + delta;
        setBalance("USD", usd, usd - current.marginUsed());
    }

    public Account toAccount(Exchange exchange, IbkrAccountSnapshot snapshot) {
        IbkrAccountSnapshot effectiveSnapshot = snapshot == null ? snapshot() : snapshot;
        Account account = new Account(exchange, "ibkr", "");
        account.setAccountId(effectiveSnapshot.accountId());
        account.setAccount(effectiveSnapshot.accountId());
        account.setBrokerName(effectiveSnapshot.broker());
        account.setExchangeId("interactive_brokers");
        account.setConnected(connectionManager.isConnected());
        account.setPaperTrading(effectiveSnapshot.paper());
        account.setSandbox(effectiveSnapshot.paper());
        account.setEquity(effectiveSnapshot.equity());
        account.setTotalBalance(effectiveSnapshot.equity());
        account.setAvailableBalance(effectiveSnapshot.availableFunds());
        account.setBuyingPower(effectiveSnapshot.buyingPower());
        account.setMarginUsed(effectiveSnapshot.marginUsed());
        account.setFreeMargin(effectiveSnapshot.availableFunds());
        account.setBalances(effectiveSnapshot.balances());
        account.setUpdatedAt(effectiveSnapshot.updatedAt());
        return account;
    }

    public void persist() {
        persistenceStore.persistAccount(snapshot());
    }
}
