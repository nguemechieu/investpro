package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class IbkrClientPortalContractSearchService implements IbkrContractSearchService {

    private final IbkrClientPortalClient clientPortalClient;

    public IbkrClientPortalContractSearchService(IbkrClientPortalClient clientPortalClient) {
        this.clientPortalClient = clientPortalClient;
    }

    @Override
    public CompletableFuture<List<IbkrContractCandidate>> search(String userSearchTerm, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (clientPortalClient == null || !clientPortalClient.isAuthenticated()) {
                throw new IllegalStateException("IBKR session is not connected.");
            }
            List<IbkrContractCandidate> candidates = clientPortalClient.searchSecurityDefinitions(userSearchTerm);
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No matching contract found.");
            }
            return candidates;
        });
    }
}
