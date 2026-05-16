package org.investpro.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors signal flow through the agent pipeline and logs each stage.
 * <p>
 * Subscribes to the {@link AgentEventBus} for:
 * <ul>
 *   <li>Signal creation</li>
 *   <li>Strategy approval / rejection</li>
 *   <li>Risk approval / rejection / alerts</li>
 *   <li>AI reasoning approval / rejection</li>
 *   <li>Order lifecycle (submitted, accepted, rejected, filled, cancelled)</li>
 * </ul>
 */
@Slf4j
public class SignalMonitorService {

    private static final List<String> MONITORED_EVENTS = List.of(
            AgentEvent.SIGNAL_CREATED,
            AgentEvent.STRATEGY_SIGNAL_APPROVED,
            AgentEvent.STRATEGY_SIGNAL_REJECTED,
            AgentEvent.RISK_APPROVED,
            AgentEvent.RISK_REJECTED,
            AgentEvent.RISK_REVIEWED,
            AgentEvent.RISK_ALERT,
            AgentEvent.REASONING_APPROVED,
            AgentEvent.REASONING_REJECTED,
            AgentEvent.ORDER_SUBMITTED,
            AgentEvent.ORDER_ACCEPTED,
            AgentEvent.ORDER_REJECTED,
            AgentEvent.ORDER_FILLED,
            AgentEvent.ORDER_CANCELLED,
            AgentEvent.POSITION_CLOSED
    );

    private final AtomicBoolean running = new AtomicBoolean(false);
    private AgentEventBus eventBus;

    public SignalMonitorService() {
    }

    /**
     * Start monitoring signal events from the given event bus.
     */
    public void start(AgentEventBus eventBus) {
        if (!running.compareAndSet(false, true)) {
            log.warn("SignalMonitorService is already running.");
            return;
        }

        this.eventBus = eventBus;

        if (eventBus == null) {
            log.warn("SignalMonitorService started with null event bus — no events will be monitored.");
            return;
        }

        for (String eventType : MONITORED_EVENTS) {
            eventBus.subscribe(eventType, this::onSignalEvent);
        }

        log.info("SignalMonitorService started — monitoring {} event types.", MONITORED_EVENTS.size());
    }

    /**
     * Stop monitoring. Existing subscriptions remain but new events are ignored.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("SignalMonitorService stopped.");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void onSignalEvent(AgentEvent event) {
        if (!running.get()) {
            return;
        }

        if (event == null) {
            return;
        }

        log.info("[SIGNAL] type={} source={} payload={} metadata={}",
                event.type(),
                event.source(),
                safePayload(event.payload()),
                event.metadata());
    }

    private String safePayload(Object payload) {
        if (payload == null) {
            return "null";
        }
        try {
            String text = payload.toString();
            return text.length() > 200 ? text.substring(0, 200) + "…" : text;
        } catch (Exception e) {
            return payload.getClass().getSimpleName();
        }
    }
}
