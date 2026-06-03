package org.investpro.exchange.ibkr;

import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.time.Instant;
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

    public Optional<Position> closePartial(TradePair pair, double quantity, double exitPrice) {
        if (pair == null || quantity <= 0.0) {
            return Optional.empty();
        }

        String symbol = pair.toString('/');
        Position position = positionsBySymbol.get(symbol);
        if (position == null) {
            return Optional.empty();
        }

        double effectiveExit = exitPrice > 0.0
                ? exitPrice
                : Math.max(position.getCurrentPrice(), position.getEntryPrice());
        position.updateCurrentPrice(effectiveExit);

        double existingQuantity = Math.max(0.0, position.getQuantity());
        double closeQuantity = Math.min(existingQuantity, quantity);
        if (closeQuantity <= 0.0) {
            return Optional.of(position);
        }

        if (closeQuantity >= existingQuantity) {
            position.close(effectiveExit);
            positionsBySymbol.remove(symbol);
            persistenceStore.persistPositions(fetchAll());
            return Optional.empty();
        }

        double realizedPerUnit = position.getSide() == Side.BUY
                ? (effectiveExit - position.getEntryPrice())
                : (position.getEntryPrice() - effectiveExit);
        position.setRealizedPnl(position.getRealizedPnl() + (realizedPerUnit * closeQuantity));
        position.setQuantity(existingQuantity - closeQuantity);
        position.setTimestamp(Instant.now());
        position.updateUnrealizedPnl();
        persistenceStore.persistPositions(fetchAll());
        return Optional.of(position);
    }

    public void setLeverage(TradePair pair, double leverage) {
        if (pair == null || leverage <= 0.0) {
            return;
        }
        Position position = positionsBySymbol.get(pair.toString('/'));
        if (position != null) {
            position.setLeverage(leverage);
            position.setTimestamp(Instant.now());
            persistenceStore.persistPositions(fetchAll());
        }
    }
}
