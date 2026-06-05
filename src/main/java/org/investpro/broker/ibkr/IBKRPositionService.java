package org.investpro.broker.ibkr;

import org.investpro.models.trading.Position;

import java.util.List;

public class IBKRPositionService {

    private final org.investpro.exchange.ibkr.IbkrExchange exchange;

    public IBKRPositionService(org.investpro.exchange.ibkr.IbkrExchange exchange) {
        this.exchange = exchange;
    }

    public List<Position> getPositions() {
        return exchange.fetchAllPositions().join();
    }
}
