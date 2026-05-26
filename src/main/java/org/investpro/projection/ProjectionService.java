package org.investpro.projection;

import org.investpro.activity.BrokerActivityEvent;

import java.util.List;

public interface ProjectionService {
    ProjectionSnapshot rebuild(List<BrokerActivityEvent> events);
}
