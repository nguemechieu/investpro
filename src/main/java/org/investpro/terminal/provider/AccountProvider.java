package org.investpro.terminal.provider;

import org.investpro.terminal.domain.AccountSnapshot;
import org.investpro.terminal.domain.Balance;
import org.investpro.terminal.domain.Position;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AccountProvider extends ProviderCapabilities {
    CompletableFuture<AccountSnapshot> accountSnapshot(String accountId);

    default CompletableFuture<List<Balance>> balances(String accountId) {
        return accountSnapshot(accountId).thenApply(AccountSnapshot::balances);
    }

    default CompletableFuture<List<Position>> positions(String accountId) {
        return accountSnapshot(accountId).thenApply(AccountSnapshot::positions);
    }
}
