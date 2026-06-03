package org.investpro.exchange.ibkr;

import org.investpro.models.trading.Position;

import java.util.List;

public final class IbkrPortfolioService {

    private final IbkrPositionService positionService;
    private final IbkrAccountService accountService;

    public IbkrPortfolioService(IbkrPositionService positionService, IbkrAccountService accountService) {
        this.positionService = positionService;
        this.accountService = accountService;
    }

    public List<Position> positions() {
        return positionService.fetchAll();
    }

    public double portfolioValue() {
        double cash = accountService.snapshot().balances().getOrDefault("USD", 0.0);
        double positionValue = positionService.fetchAll().stream().mapToDouble(Position::getCurrentValue).sum();
        return cash + positionValue;
    }
}
