package org.investpro.exchange.contracts;

import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface PositionProvider {

    CompletableFuture<List<Position>> fetchPositions(TradePair tradePair);

    CompletableFuture<List<Position>> fetchAllPositions();

    CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair);

    CompletableFuture<String> closePosition(TradePair tradePair);

    CompletableFuture<String> closeAllPositions();

    CompletableFuture<String> closePosition(TradePair symbol, String positionId);

    CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity);

    CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss);

    CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit);

    CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance);
}