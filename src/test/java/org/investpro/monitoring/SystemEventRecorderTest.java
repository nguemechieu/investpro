package org.investpro.monitoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemEventRecorderTest {

    @Test
    void shouldTrackHeartbeatAndStructuredErrorCounters() {
        SystemEventRecorder recorder = new SystemEventRecorder();

        recorder.recordHeartbeat("test");
        recorder.recordMarketTick();
        recorder.recordOrderRejected("rejected");
        recorder.recordExecutionError("boom");
        recorder.recordAccountError("account");
        recorder.recordWebSocketDisconnect();

        assertThat(recorder.getLastHeartbeatAt()).isNotNull();
        assertThat(recorder.getLastHeartbeatSource()).isEqualTo("test");
        assertThat(recorder.snapshotCounters())
                .containsEntry("heartbeatCount", 1L)
                .containsEntry("marketTickCount", 1L)
                .containsEntry("orderRejectedCount", 1L)
                .containsEntry("executionErrorCount", 1L)
                .containsEntry("accountErrorCount", 1L)
                .containsEntry("webSocketDisconnectCount", 1L);
    }
}
