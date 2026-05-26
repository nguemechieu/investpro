package org.investpro.operations;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.execution.ExecutionMode;
import org.investpro.projection.ProjectionSnapshot;
import org.investpro.reconciliation.ReconciliationStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class PipelineSystemOperationsService {
    private final BrokerActivityRepository repository;

    public PipelineSystemOperationsService(BrokerActivityRepository repository) {
        this.repository = repository;
    }

    public SystemOperationsSnapshot snapshot(ProjectionSnapshot projection,
                                             ReconciliationStatus reconciliationStatus,
                                             ExecutionMode executionMode,
                                             List<String> warnings,
                                             List<String> errors) {
        BrokerActivityEvent latest = repository == null ? null : repository.findByTimeRange(null, null, Instant.EPOCH, Instant.now())
                .stream()
                .max(Comparator.comparing(BrokerActivityEvent::getEventTime))
                .orElse(null);
        return new SystemOperationsSnapshot(
                PipelineComponentStatus.OK,
                PipelineComponentStatus.OK,
                PipelineComponentStatus.OK,
                errors == null || errors.isEmpty() ? PipelineComponentStatus.OK : PipelineComponentStatus.DEGRADED,
                PipelineComponentStatus.OK,
                PipelineComponentStatus.UNKNOWN,
                repository == null ? PipelineComponentStatus.DOWN : PipelineComponentStatus.OK,
                reconciliationStatus,
                executionMode,
                latest,
                projection == null ? 0 : projection.orders().orders().size(),
                projection == null ? 0 : projection.positions().positions().size(),
                warnings,
                errors,
                Instant.now());
    }
}
