package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class IbkrAdaptiveContractSearchService implements IbkrContractSearchService {

    private final IbkrConnectionManager connectionManager;
    private final IbkrContractSearchService twsSearchService;
    private final IbkrContractSearchService clientPortalSearchService;
    private final IbkrClientPortalClient clientPortalClient;

    public IbkrAdaptiveContractSearchService(IbkrConnectionManager connectionManager,
            IbkrContractSearchService twsSearchService,
            IbkrContractSearchService clientPortalSearchService,
            IbkrClientPortalClient clientPortalClient) {
        this.connectionManager = connectionManager;
        this.twsSearchService = twsSearchService;
        this.clientPortalSearchService = clientPortalSearchService;
        this.clientPortalClient = clientPortalClient;
    }

    @Override
    public CompletableFuture<List<IbkrContractCandidate>> search(String userSearchTerm, Duration timeout) {
        if (connectionManager != null
                && connectionManager.getConnectionMode() == IbkrConnectionMode.CLIENT_PORTAL_GATEWAY) {
            return clientPortalSearchService.search(userSearchTerm, timeout);
        }
        if (clientPortalClient != null && clientPortalClient.isAuthenticated()) {
            return clientPortalSearchService.search(userSearchTerm, timeout);
        }
        return twsSearchService.search(userSearchTerm, timeout);
    }
}
