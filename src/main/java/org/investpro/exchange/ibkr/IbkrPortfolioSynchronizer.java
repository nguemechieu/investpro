package org.investpro.exchange.ibkr;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;

import java.util.List;

@Slf4j
public final class IbkrPortfolioSynchronizer {

    private final IbkrPersistenceStore persistenceStore;

    public IbkrPortfolioSynchronizer(IbkrPersistenceStore persistenceStore) {
        this.persistenceStore = persistenceStore;
    }

    public void synchronize(IbkrAccountSnapshot snapshot,
            List<Position> positions,
            List<OpenOrder> orders,
            List<String> executions) {
        persistenceStore.persistAccount(snapshot);
        persistenceStore.persistPositions(positions);
        persistenceStore.persistOrders(orders);
        persistenceStore.persistExecutions(executions);
        log.debug("IBKR portfolio synchronization completed: positions={}, orders={}, executions={}",
                positions.size(),
                orders.size(),
                executions.size());
    }
}
