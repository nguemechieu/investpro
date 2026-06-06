package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class IbkrAdaptiveContractDetailsService implements IbkrContractDetailsService {

    private final IbkrConnectionManager connectionManager;
    private final IbkrContractDetailsService twsDetailsService;
    private final IbkrContractDetailsService clientPortalDetailsService;
    private final IbkrClientPortalClient clientPortalClient;

    public IbkrAdaptiveContractDetailsService(IbkrConnectionManager connectionManager,
            IbkrContractDetailsService twsDetailsService,
            IbkrContractDetailsService clientPortalDetailsService,
            IbkrClientPortalClient clientPortalClient) {
        this.connectionManager = connectionManager;
        this.twsDetailsService = twsDetailsService;
        this.clientPortalDetailsService = clientPortalDetailsService;
        this.clientPortalClient = clientPortalClient;
    }

    @Override
    public CompletableFuture<IbkrResolvedContract> requestDetails(IbkrContractCandidate candidate, Duration timeout) {
        if (connectionManager != null
                && connectionManager.getConnectionMode() == IbkrConnectionMode.CLIENT_PORTAL_GATEWAY) {
            return clientPortalDetailsService.requestDetails(candidate, timeout);
        }
        if (clientPortalClient != null && clientPortalClient.isAuthenticated()) {
            return clientPortalDetailsService.requestDetails(candidate, timeout);
        }
        return twsDetailsService.requestDetails(candidate, timeout);
    }
}
