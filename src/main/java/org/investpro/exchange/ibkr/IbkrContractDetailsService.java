package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface IbkrContractDetailsService {
    CompletableFuture<IbkrResolvedContract> requestDetails(IbkrContractCandidate candidate, Duration timeout);
}
