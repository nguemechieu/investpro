package org.investpro.execution.routing;

import org.investpro.execution.OrderIntent;

public interface VenueScorer {
    VenueScore score(String venueId, OrderIntent intent);
}
