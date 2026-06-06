package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class IbkrTwsContractDetailsService implements IbkrContractDetailsService {

    private final IbkrConnectionManager connectionManager;
    private final IbkrTwsContractGateway twsContractGateway;

    public IbkrTwsContractDetailsService(IbkrConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.twsContractGateway = new IbkrTwsContractGateway(connectionManager);
    }

    @Override
    public CompletableFuture<IbkrResolvedContract> requestDetails(IbkrContractCandidate candidate, Duration timeout) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("IBKR session is not connected."));
        }
        if (!twsContractGateway.isOfficialApiAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API is not ready."));
        }
        return twsContractGateway.reqContractDetails(candidate, timeout);
    }
}
