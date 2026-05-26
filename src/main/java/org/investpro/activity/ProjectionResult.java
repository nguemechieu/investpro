package org.investpro.activity;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectionResult {
    String exchangeId;
    String eventId;
    BrokerActivityType activityType;
    boolean applied;
    boolean duplicate;
    boolean criticalRiskEvent;
    String message;

    public static ProjectionResult applied(BrokerActivityEvent event, boolean criticalRiskEvent, String message) {
        return ProjectionResult.builder()
                .exchangeId(event.getExchangeId())
                .eventId(event.getEventId())
                .activityType(event.getActivityType())
                .applied(true)
                .criticalRiskEvent(criticalRiskEvent)
                .message(message)
                .build();
    }

    public static ProjectionResult failed(BrokerActivityEvent event, String message) {
        return ProjectionResult.builder()
                .exchangeId(event == null ? null : event.getExchangeId())
                .eventId(event == null ? null : event.getEventId())
                .activityType(event == null ? BrokerActivityType.UNKNOWN : event.getActivityType())
                .applied(false)
                .message(message)
                .build();
    }
}
