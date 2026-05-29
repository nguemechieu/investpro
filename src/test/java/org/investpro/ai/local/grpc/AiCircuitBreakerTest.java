package org.investpro.ai.local.grpc;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiCircuitBreakerTest {

    @Test
    void opensAfterThresholdFailures() {
        AiCircuitBreaker breaker = new AiCircuitBreaker(2, Duration.ofSeconds(5));

        assertThat(breaker.allowRequest()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(AiCircuitBreaker.State.CLOSED);

        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(AiCircuitBreaker.State.OPEN);
        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    void closesOnSuccess() {
        AiCircuitBreaker breaker = new AiCircuitBreaker(1, Duration.ofMillis(5));
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(AiCircuitBreaker.State.OPEN);

        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(AiCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowRequest()).isTrue();
    }
}
