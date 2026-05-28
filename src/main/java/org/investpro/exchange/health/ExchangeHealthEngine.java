package org.investpro.exchange.health;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeHealthScore;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.model.EndpointHealthSnapshot;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.exchange.resilience.model.ExchangeHealthGrade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Computes and tracks per-exchange health scores, emitting grade-change events
 * on the {@link AgentEventBus} whenever an exchange transitions between health grades.
 *
 * <p>Health scores are computed using {@link ExchangeHealthScore#compute(Map, ExchangeTelemetryEngine.ExchangeTelemetrySnapshot)}
 * from live telemetry and endpoint snapshots stored via
 * {@link #updateTelemetry(String, ExchangeTelemetryEngine, Map)}.
 */
@Slf4j
public class ExchangeHealthEngine {

    private static final String SOURCE = "ExchangeHealthEngine";
    private static final String EVENT_TYPE = "EXCHANGE_HEALTH_CHANGED";

    @Nullable
    private final AgentEventBus eventBus;

    private final ConcurrentHashMap<String, ExchangeTelemetryEngine> telemetryMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<EndpointType, EndpointHealthSnapshot>> snapshotMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExchangeHealthScore> lastScores = new ConcurrentHashMap<>();

    /**
     * Constructs a health engine with an optional event bus for grade-change notifications.
     *
     * @param eventBus event bus to publish grade-change events; may be null (events silently dropped)
     */
    public ExchangeHealthEngine(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
        log.info("ExchangeHealthEngine initialized (eventBus={})", eventBus != null ? "present" : "absent");
    }

    // ─── Telemetry Ingestion ─────────────────────────────────────────

    /**
     * Stores the latest telemetry and endpoint snapshots for an exchange.
     * This data is used by {@link #computeScore(String)} to derive health scores.
     *
     * @param exchangeName the exchange identifier
     * @param telemetry    the telemetry engine holding live metrics for this exchange
     * @param snapshots    per-endpoint health snapshots
     */
    public void updateTelemetry(
            @NotNull String exchangeName,
            @NotNull ExchangeTelemetryEngine telemetry,
            @NotNull Map<EndpointType, EndpointHealthSnapshot> snapshots
    ) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        Objects.requireNonNull(telemetry, "telemetry must not be null");
        Objects.requireNonNull(snapshots, "snapshots must not be null");
        telemetryMap.put(exchangeName, telemetry);
        snapshotMap.put(exchangeName, new HashMap<>(snapshots));
        log.debug("Updated telemetry for exchange: {}", exchangeName);
    }

    // ─── Score Computation ──────────────────────────────────────────

    /**
     * Computes the current health score for a single exchange using stored telemetry.
     * If the resulting grade differs from the last known grade, publishes a grade-change event.
     *
     * @param exchangeName the exchange identifier
     * @return an {@link Optional} containing the computed score, or empty if no telemetry is available
     */
    public Optional<ExchangeHealthScore> computeScore(@NotNull String exchangeName) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        ExchangeTelemetryEngine telemetry = telemetryMap.get(exchangeName);
        Map<EndpointType, EndpointHealthSnapshot> snapshots = snapshotMap.get(exchangeName);
        if (telemetry == null || snapshots == null) {
            return Optional.empty();
        }

        ExchangeHealthScore score = ExchangeHealthScore.compute(snapshots, telemetry.snapshot());
        ExchangeHealthScore previous = lastScores.put(exchangeName, score);

        if (previous == null || previous.grade() != score.grade()) {
            log.info("Health grade changed for {}: {} → {}", exchangeName,
                    previous == null ? "NONE" : previous.grade(), score.grade());
            publishGradeChange(exchangeName);
        }

        return Optional.of(score);
    }

    /**
     * Computes health scores for all exchanges that have stored telemetry.
     *
     * @return map of exchange name → computed health score
     */
    public Map<String, ExchangeHealthScore> computeAllScores() {
        Map<String, ExchangeHealthScore> results = new LinkedHashMap<>();
        for (String exchangeName : telemetryMap.keySet()) {
            computeScore(exchangeName).ifPresent(score -> results.put(exchangeName, score));
        }
        return results;
    }

    // ─── State Queries ─────────────────────────────────────────────

    /**
     * Returns the most recently computed health score for an exchange.
     *
     * @param exchangeName the exchange identifier
     * @return the last computed score, or empty if none has been computed yet
     */
    public Optional<ExchangeHealthScore> getLastScore(@NotNull String exchangeName) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        return Optional.ofNullable(lastScores.get(exchangeName));
    }

    /**
     * Returns the most recently computed health grade for an exchange.
     *
     * @param exchangeName the exchange identifier
     * @return the last computed grade, or empty if none has been computed yet
     */
    public Optional<ExchangeHealthGrade> getLastGrade(@NotNull String exchangeName) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        return getLastScore(exchangeName).map(ExchangeHealthScore::grade);
    }

    /**
     * Returns all exchange names whose last computed grade equals the given grade.
     *
     * @param grade the health grade to filter by
     * @return list of exchange names in that grade
     */
    public List<String> getExchangesInGrade(@NotNull ExchangeHealthGrade grade) {
        Objects.requireNonNull(grade, "grade must not be null");
        return lastScores.entrySet().stream()
                .filter(e -> e.getValue().grade() == grade)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Returns a summary report mapping each exchange name to its last score's summary string.
     * Exchanges with no computed score are omitted.
     *
     * @return map of exchange name → score summary
     */
    public Map<String, String> getSummaryReport() {
        Map<String, String> report = new LinkedHashMap<>();
        for (Map.Entry<String, ExchangeHealthScore> entry : lastScores.entrySet()) {
            report.put(entry.getKey(), entry.getValue().summary());
        }
        return report;
    }

    // ─── Internal ────────────────────────────────────────────────

    private void publishGradeChange(String exchangeName) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(AgentEvent.of(EVENT_TYPE, SOURCE, exchangeName));
        } catch (Exception e) {
            log.warn("Failed to publish grade-change event for {}: {}", exchangeName, e.getMessage());
        }
    }
}
