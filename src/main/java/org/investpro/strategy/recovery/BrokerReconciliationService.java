package org.investpro.strategy.recovery;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyAssignment;

import java.time.Instant;
import java.util.*;

/**
 * Compares persisted assignments with broker state and reports reconciliation
 * issues.
 */
@Slf4j
public final class BrokerReconciliationService {

    public enum Issue {
        ORPHANED_POSITION,
        POSITION_CLOSED_EXTERNALLY,
        ASSIGNMENT_MISSING,
        BROKER_SYNC_FAILED
    }

    public record ReconciliationFinding(
            String symbol,
            String assignmentId,
            Issue issue,
            String brokerPositionId,
            String reason) {
    }

    public record ReconciliationResult(
            boolean successful,
            Instant completedAt,
            List<ReconciliationFinding> findings) {

        public boolean hasFailures() {
            return findings.stream().anyMatch(f -> f.issue() == Issue.BROKER_SYNC_FAILED);
        }

        public boolean hasOrphanedPositions() {
            return findings.stream().anyMatch(f -> f.issue() == Issue.ORPHANED_POSITION);
        }
    }

    public ReconciliationResult reconcile(
            Exchange exchange,
            Collection<StrategyAssignment> assignments,
            Map<String, StrategyPositionOwnership> ownershipByPositionId) {

        List<ReconciliationFinding> findings = new ArrayList<>();
        if (exchange == null) {
            findings.add(new ReconciliationFinding("", "", Issue.BROKER_SYNC_FAILED, "", "Exchange unavailable"));
            return new ReconciliationResult(false, Instant.now(), List.copyOf(findings));
        }

        List<Position> brokerPositions = new ArrayList<>();

        Map<String, StrategyAssignment> assignmentBySymbol = new HashMap<>();
        if (assignments != null) {
            for (StrategyAssignment assignment : assignments) {
                if (assignment == null || assignment.getSymbol() == null) {
                    continue;
                }
                assignmentBySymbol.put(assignment.getSymbol().toUpperCase(Locale.ROOT), assignment);

                try {
                    TradePair pair = TradePair.fromSymbol(assignment.getSymbol());
                    List<Position> positions = exchange.fetchPositions(pair).join();
                    if (positions != null) {
                        brokerPositions.addAll(positions);
                    }
                } catch (Exception ex) {
                    findings.add(new ReconciliationFinding(
                            assignment.getSymbol(),
                            assignment.getAssignmentId(),
                            Issue.BROKER_SYNC_FAILED,
                            "",
                            ex.getMessage()));
                }
            }
        }

        Map<String, StrategyPositionOwnership> ownership = ownershipByPositionId == null
                ? Map.of()
                : ownershipByPositionId;

        for (Position position : brokerPositions) {
            if (position == null) {
                continue;
            }

            String symbol = Objects.requireNonNullElse(position.getSymbol(), "");
            String normalizedSymbol = symbol.toUpperCase(Locale.ROOT);
            String brokerPositionId = Objects.requireNonNullElse(position.getPositionId(), "");

            StrategyAssignment assignment = assignmentBySymbol.get(normalizedSymbol);
            if (assignment == null) {
                findings.add(new ReconciliationFinding(
                        symbol,
                        "",
                        Issue.ORPHANED_POSITION,
                        brokerPositionId,
                        "Open broker position has no matching active assignment"));
                continue;
            }

            StrategyPositionOwnership owned = ownership.get(brokerPositionId);
            if (owned == null) {
                findings.add(new ReconciliationFinding(
                        symbol,
                        assignment.getAssignmentId(),
                        Issue.ASSIGNMENT_MISSING,
                        brokerPositionId,
                        "Assignment exists but ownership record is missing"));
            }
        }

        Set<String> brokerSymbols = new HashSet<>();
        for (Position position : brokerPositions) {
            if (position != null && position.getSymbol() != null) {
                brokerSymbols.add(position.getSymbol().toUpperCase(Locale.ROOT));
            }
        }

        if (assignments != null) {
            for (StrategyAssignment assignment : assignments) {
                if (assignment == null || assignment.getSymbol() == null) {
                    continue;
                }
                if (!brokerSymbols.contains(assignment.getSymbol().toUpperCase(Locale.ROOT))) {
                    findings.add(new ReconciliationFinding(
                            assignment.getSymbol(),
                            assignment.getAssignmentId(),
                            Issue.POSITION_CLOSED_EXTERNALLY,
                            "",
                            "No broker position found for active assignment"));
                }
            }
        }

        return new ReconciliationResult(true, Instant.now(), List.copyOf(findings));
    }
}
