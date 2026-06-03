package org.investpro.exchange.ibkr;

import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class IbkrPositionService {

    private final ConcurrentHashMap<String, Position> positionsBySymbol = new ConcurrentHashMap<>();
    private final IbkrPersistenceStore persistenceStore;

    public IbkrPositionService(IbkrPersistenceStore persistenceStore) {
        this.persistenceStore = persistenceStore;
    }

    public List<Position> fetchAll() {
        return List.copyOf(positionsBySymbol.values());
    }

    public List<Position> fetchFor(TradePair pair) {
        Position position = positionsBySymbol.get(pair.toString('/'));
        return position == null ? List.of() : List.of(position);
    }

    public Optional<Position> fetchOne(TradePair pair) {
        return Optional.ofNullable(positionsBySymbol.get(pair.toString('/')));
    }

    public void upsert(TradePair pair, Side side, double quantity, double fillPrice) {
        positionsBySymbol.compute(pair.toString('/'), (key, existing) -> {
            if (existing == null) {
                return new Position(pair, side, quantity, fillPrice);
            }
            existing.setSide(side);
            existing.setQuantity(quantity);
            existing.updateCurrentPrice(fillPrice);
            return existing;
        });
        persistenceStore.persistPositions(fetchAll());
    }

    public void close(TradePair pair) {
        Position removed = positionsBySymbol.remove(pair.toString('/'));
        if (removed != null) {
            removed.close();
        }
        persistenceStore.persistPositions(fetchAll());
    }

    public void closeAll() {
        positionsBySymbol.values().forEach(Position::close);
        positionsBySymbol.clear();
        persistenceStore.persistPositions(fetchAll());
    }

    public void setStopLoss(TradePair pair, double stopLoss) {
        Position position = positionsBySymbol.get(pair.toString('/'));
        if (position != null) {
            position.setStopLoss(stopLoss);
            persistenceStore.persistPositions(fetchAll());
        }
    }

    public void setTakeProfit(TradePair pair, double takeProfit) {
        Position position = positionsBySymbol.get(pair.toString('/'));
        if (position != null) {
            position.setTakeProfit(takeProfit);
            persistenceStore.persistPositions(fetchAll());
        }
    }
}
