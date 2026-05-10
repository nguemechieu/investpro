package org.investpro.exchange.contracts;



import org.investpro.models.Account;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface AccountProvider {

    Account getUserAccountDetails() throws ExecutionException, InterruptedException;

    CompletableFuture<Account> fetchAccount();

    CompletableFuture<Double> fetchAvailableBalance(String currencyCode);

    CompletableFuture<Double> fetchTotalBalance(String currencyCode);

    CompletableFuture<Double> fetchEquity();

    CompletableFuture<Double> fetchMarginUsed();

    CompletableFuture<Double> fetchFreeMargin();
}