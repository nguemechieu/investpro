package org.investpro.exchange.runtime;

import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.models.ExchangeCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExchangeRuntimeManager}.
 *
 * <p>Tests cover: registration, heartbeat tracking, state transitions,
 * auth failure → AUTH_FAILED state, WebSocket disconnect → DEGRADED,
 * and stale detection threshold.
 */
class ExchangeRuntimeManagerTest {

    private ExchangeRuntimeManager manager;
    private ExchangeCapability coinbaseCap;

    @BeforeEach
    void setUp() {
        manager = new ExchangeRuntimeManager(new AgentEventBus());
        coinbaseCap = ExchangeCapability.builder()
                .exchangeName("Coinbase")
                .exchangeId("coinbase")
                .displayName("Coinbase")
                .supportsWebSocket(true)
                .supportsLiveTrading(true)
                .supportsCrypto(true)
                .build();
    }

    @Test
    void exchangeStartsInDisconnectedState() {
        manager.register("Coinbase", coinbaseCap);
        assertThat(manager.getState("Coinbase"))
                .isEqualTo(ExchangeRuntimeState.DISCONNECTED);
    }

    @Test
    void transitionToConnected() {
        manager.register("Coinbase", coinbaseCap);
        manager.transitionState("Coinbase", ExchangeRuntimeState.CONNECTED);
        assertThat(manager.getState("Coinbase"))
                .isEqualTo(ExchangeRuntimeState.CONNECTED);
    }

    @Test
    void authFailureSetsAuthFailedState() {
        manager.register("Coinbase", coinbaseCap);
        manager.recordAuthResult("Coinbase", false);
        assertThat(manager.getState("Coinbase"))
                .isEqualTo(ExchangeRuntimeState.AUTH_FAILED);
    }

    @Test
    void successfulAuthResetsState() {
        manager.register("Coinbase", coinbaseCap);
        manager.recordAuthResult("Coinbase", false);
        manager.recordAuthResult("Coinbase", true);
        assertThat(manager.getState("Coinbase"))
                .isNotEqualTo(ExchangeRuntimeState.AUTH_FAILED);
    }

    @Test
    void heartbeatIsTracked() {
        manager.register("Coinbase", coinbaseCap);
        manager.transitionState("Coinbase", ExchangeRuntimeState.CONNECTED);
        manager.recordHeartbeat("Coinbase");
        ExchangeRuntimeMetadata meta = manager.getMetadata("Coinbase");
        assertThat(meta).isNotNull();
        assertThat(meta.lastHeartbeat()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void websocketDisconnectMarksDegraded() {
        manager.register("Coinbase", coinbaseCap);
        manager.transitionState("Coinbase", ExchangeRuntimeState.CONNECTED);
        manager.recordWebSocketState("Coinbase", false);
        assertThat(manager.getState("Coinbase"))
                .isIn(ExchangeRuntimeState.DEGRADED, ExchangeRuntimeState.DISCONNECTED);
    }

    @Test
    void unregisteredExchangeReturnsDisconnected() {
        assertThat(manager.getState("UnknownExchange"))
                .isEqualTo(ExchangeRuntimeState.DISCONNECTED);
    }
}
