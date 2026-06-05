package org.investpro.broker.ibkr;

import org.investpro.models.Account;

public class IBKRAccountService {

    private final org.investpro.exchange.ibkr.IbkrExchange exchange;

    public IBKRAccountService(org.investpro.exchange.ibkr.IbkrExchange exchange) {
        this.exchange = exchange;
    }

    public Account getAccount() {
        return exchange.fetchAccount().join();
    }
}
