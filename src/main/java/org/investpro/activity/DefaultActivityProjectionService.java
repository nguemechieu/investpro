package org.investpro.activity;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DefaultActivityProjectionService implements ActivityProjectionService {
    private final BrokerActivityRepository activityRepository;

    public DefaultActivityProjectionService(BrokerActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public ProjectionResult apply(BrokerActivityEvent event) {
        if (event == null) {
            return ProjectionResult.failed(null, "Broker activity event is null");
        }
        boolean duplicate = activityRepository.findByEventId(event.getExchangeId(), event.getEventId()).isPresent();
        activityRepository.save(event);
        boolean critical = event.getActivityType() == BrokerActivityType.MARGIN_CLOSEOUT
                || event.getActivityType() == BrokerActivityType.LIQUIDATION
                || event.getActivityType() == BrokerActivityType.MARGIN_CALL;
        if (duplicate) {
            return ProjectionResult.builder()
                    .exchangeId(event.getExchangeId())
                    .eventId(event.getEventId())
                    .activityType(event.getActivityType())
                    .applied(false)
                    .duplicate(true)
                    .criticalRiskEvent(critical)
                    .message("Duplicate broker activity event ignored")
                    .build();
        }
        log.debug("Projected broker activity event {}:{} as {}", event.getExchangeId(), event.getEventId(), event.getActivityType());
        return ProjectionResult.applied(event, critical, "Broker activity event stored for projection");
    }

    @Override
    public ProjectionBatchResult applyAll(List<BrokerActivityEvent> events) {
        ProjectionBatchResult.ProjectionBatchResultBuilder builder = ProjectionBatchResult.builder();
        int applied = 0;
        int skipped = 0;
        int failed = 0;
        if (events != null) {
            for (BrokerActivityEvent event : events) {
                ProjectionResult result = apply(event);
                builder.result(result);
                if (result.isApplied()) {
                    applied++;
                } else if (result.isDuplicate()) {
                    skipped++;
                } else {
                    failed++;
                }
            }
        }
        return builder
                .attempted(events == null ? 0 : events.size())
                .applied(applied)
                .skipped(skipped)
                .failed(failed)
                .build();
    }
}
