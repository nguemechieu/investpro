package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IbkrContractSearchService {
    CompletableFuture<List<IbkrContractCandidate>> search(String userSearchTerm, Duration timeout);
}
