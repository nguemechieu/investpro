package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class IbkrClientPortalContractDetailsService implements IbkrContractDetailsService {

    private final IbkrClientPortalClient clientPortalClient;

    public IbkrClientPortalContractDetailsService(IbkrClientPortalClient clientPortalClient) {
        this.clientPortalClient = clientPortalClient;
    }

    @Override
    public CompletableFuture<IbkrResolvedContract> requestDetails(IbkrContractCandidate candidate, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (clientPortalClient == null || !clientPortalClient.isAuthenticated()) {
                throw new IllegalStateException("IBKR session is not connected.");
            }
            if (candidate == null) {
                throw new IllegalArgumentException("No matching contract found.");
            }
            return clientPortalClient.fetchSecurityDefinitionDetails(candidate)
                    .orElseThrow(() -> new IllegalStateException("Contract details request timed out."));
        });
    }
}
