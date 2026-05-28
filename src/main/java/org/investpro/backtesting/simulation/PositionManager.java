package org.investpro.backtesting.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Position container prepared for scaling, partial exits, and multi-position
 * simulations. Legacy mode uses the first active position.
 */
public final class PositionManager {
    private final List<Position> positions = new ArrayList<>(4);

    public PositionManager() {
        positions.add(new Position());
    }

    public Position activePosition() {
        return positions.get(0);
    }

    public boolean hasOpenPosition() {
        return activePosition().open();
    }

    public void reset() {
        for (Position position : positions) {
            position.reset();
        }
    }

    public int openPositionCount() {
        int count = 0;
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i).open()) {
                count++;
            }
        }
        return count;
    }
}
