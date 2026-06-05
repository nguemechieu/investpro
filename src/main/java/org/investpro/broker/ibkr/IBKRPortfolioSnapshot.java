package org.investpro.broker.ibkr;

import org.investpro.models.Account;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.Position;

import java.time.Instant;
import java.util.List;

public record IBKRPortfolioSnapshot(
        Account account,
        List<Position> positions,
        List<Order> orders,
        Instant capturedAt) {
}
