package org.investpro.activity;

import java.util.List;

public interface ActivityProjectionService {
    ProjectionResult apply(BrokerActivityEvent event);

    ProjectionBatchResult applyAll(List<BrokerActivityEvent> events);
}
