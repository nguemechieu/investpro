package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiAuditLogger;
import org.investpro.ai.AiPositionManagementResponse;
import org.investpro.ai.PositionActionFinalGate;
import org.investpro.ai.PositionActionIntent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.models.trading.Position;

import java.util.Map;

/**
 * Agent responsible for position lifecycle management.
 * <p>
 * Processes:
 * - Approved execution signals
 * - Position entry/exit
 * - Stop loss and take profit management
 * - Position maintenance
 * <p>
 * Publishes:
 * - Position updates
 * - Entry/exit events
 * - Position maintenance actions
 */
@Slf4j
public class PositionManagementAgent implements org.investpro.core.agents.Agent {
    private volatile boolean running = false;
    private AgentContext context;
    private final AiAuditLogger auditLogger = new AiAuditLogger("logs/ai-position-actions.jsonl");

    @Override
    public String name() {
        return "PositionManagementAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("PositionManagementAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("PositionManagementAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("PositionManagementAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Position management agent processes execution and position events
            if (AgentEvent.ORDER_SUBMITTED.equals(event.type()) ||
                    AgentEvent.POSITION_UPDATE.equals(event.type()) ||
                    AgentEvent.POSITION_CLOSED.equals(event.type())) {
                managePosition(event);
            }

        } catch (Exception e) {
            log.error("Error processing position event", e);
        }
    }

    private void managePosition(AgentEvent event) {
        Position position = resolvePosition(event);
        AiPositionManagementResponse aiRecommendation = resolveAiRecommendation(event);

        if (position == null) {
            log.debug("PositionManagementAgent observed {} without a position-management recommendation", event.type());
            return;
        }
        if (aiRecommendation == null) {
            aiRecommendation = AiPositionManagementResponse.holdRecommendation(
                    "No AI position-management recommendation supplied for this position update",
                    name());
        } else if (!aiRecommendation.isValid()) {
            aiRecommendation = AiPositionManagementResponse.failedResponse(
                    "Invalid AI position-management recommendation",
                    name());
        }

        PositionActionIntent intent = PositionActionFinalGate.makeDecision(position, aiRecommendation);
        auditLogger.logPositionReview(
                position.getPositionId(),
                position.getTradePair() == null ? "UNKNOWN" : position.getTradePair().toString('/'),
                null,
                aiRecommendation,
                intent);

        if (intent == null || !intent.isApproved()) {
            publish(AgentEvent.POSITION_ACTION_REJECTED, aiRecommendation);
            log.info("Position action rejected by final gate: position={} action={}",
                    position.getPositionId(), aiRecommendation.getAction());
            return;
        }

        if (intent.isRiskyAction()) {
            publish(AgentEvent.POSITION_ACTION_RISKY, intent);
            log.warn("Risky position action requires heightened audit: position={} action={} confidence={}",
                    position.getPositionId(), intent.getAction(), intent.getConfidence());
        }

        publish(AgentEvent.POSITION_ACTION_APPROVED, intent);
        log.info("Position action approved by final gate: position={} action={} confidence={}",
                position.getPositionId(), intent.getAction(), intent.getConfidence());
    }

    private Position resolvePosition(AgentEvent event) {
        if (event.payload() instanceof Position position) {
            return position;
        }
        Object metadataPosition = event.metadata().get("position");
        if (metadataPosition instanceof Position position) {
            return position;
        }
        if (event.payload() instanceof Map<?, ?> map) {
            Object value = map.get("position");
            if (value instanceof Position position) {
                return position;
            }
        }
        return null;
    }

    private AiPositionManagementResponse resolveAiRecommendation(AgentEvent event) {
        if (event.payload() instanceof AiPositionManagementResponse response) {
            return response;
        }
        Object metadataRecommendation = event.metadata().get("aiRecommendation");
        if (metadataRecommendation instanceof AiPositionManagementResponse response) {
            return response;
        }
        Object metadataPositionRecommendation = event.metadata().get("aiPositionRecommendation");
        if (metadataPositionRecommendation instanceof AiPositionManagementResponse response) {
            return response;
        }
        if (event.payload() instanceof Map<?, ?> map) {
            Object value = map.get("aiRecommendation");
            if (value instanceof AiPositionManagementResponse response) {
                return response;
            }
        }
        return null;
    }

    private void publish(String eventType, Object payload) {
        if (context == null || context.getEventBus() == null) {
            return;
        }
        context.getEventBus().publish(AgentEvent.of(eventType, name(), payload));
    }
}
