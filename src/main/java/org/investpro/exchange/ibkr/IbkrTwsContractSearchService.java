package org.investpro.exchange.ibkr;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class IbkrTwsContractSearchService implements IbkrContractSearchService {

    private final IbkrConnectionManager connectionManager;
    private final IbkrTwsContractGateway twsContractGateway;

    public IbkrTwsContractSearchService(IbkrConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.twsContractGateway = new IbkrTwsContractGateway(connectionManager);
    }

    @Override
    public CompletableFuture<List<IbkrContractCandidate>> search(String userSearchTerm, Duration timeout) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("IBKR session is not connected."));
        }
        String normalized = IbkrContractResolver.normalizeSearchTerm(userSearchTerm);
        if (looksLikeForex(normalized)) {
            String[] parts = normalized.split("/");
            IbkrContractCandidate candidate = new IbkrContractCandidate(
                    null,
                    parts[0],
                    parts[0] + "/" + parts[1],
                    IbkrSecurityType.FOREX,
                    "CASH",
                    "IDEALPRO",
                    "IDEALPRO",
                    parts[1],
                    parts[0] + "." + parts[1],
                    parts[0] + "." + parts[1],
                    "",
                    "",
                    "",
                    "TWS_API",
                    "{\"syntheticCashContract\":true}");
            return CompletableFuture.completedFuture(List.of(candidate));
        }
        if (!twsContractGateway.isOfficialApiAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API is not ready."));
        }
        return twsContractGateway.reqMatchingSymbols(normalized.replace("/", ""), timeout)
                .thenCompose(candidates -> {
                    if (!candidates.isEmpty()) {
                        return CompletableFuture.completedFuture(candidates);
                    }
                    return twsContractGateway.reqContractDetails(new IbkrContractCandidate(
                            null,
                            normalized.replace("/", ""),
                            normalized,
                            IbkrSecurityType.FUTURE,
                            "FUT",
                            "GLOBEX",
                            "",
                            "USD",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "TWS_API",
                            ""), timeout).thenApply(contract -> List.of(new IbkrContractCandidate(
                                    contract.conId(),
                                    contract.symbol(),
                                    contract.longName(),
                                    IbkrSecurityType.fromIbkrCode(contract.secType()),
                                    contract.secType(),
                                    contract.exchange(),
                                    contract.primaryExchange(),
                                    contract.currency(),
                                    contract.localSymbol(),
                                    contract.tradingClass(),
                                    contract.lastTradeDateOrContractMonth(),
                                    contract.multiplier(),
                                    "",
                                    "TWS_API",
                                    contract.metadataJson())));
                });
    }

    private boolean looksLikeForex(String normalized) {
        String[] parts = normalized == null ? new String[0] : normalized.split("/");
        return parts.length == 2 && parts[0].length() == 3 && parts[1].length() == 3;
    }
}
