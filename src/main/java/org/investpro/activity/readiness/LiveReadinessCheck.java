package org.investpro.activity.readiness;

import lombok.Builder;
import lombok.Value;

/**
 * Result of a single live-readiness pre-flight check.
 */
@Value
@Builder
public class LiveReadinessCheck {
    String name;
    LiveReadinessStatus status;
    String message;
    String fixHint;
    boolean critical;
    boolean userOverridable;

    public static LiveReadinessCheck pass(String name) {
        return builder().name(name).status(LiveReadinessStatus.PASS).message("OK").build();
    }

    public static LiveReadinessCheck warn(String name, String msg, String fix) {
        return builder().name(name).status(LiveReadinessStatus.WARN)
                .message(msg).fixHint(fix).userOverridable(true).build();
    }

    public static LiveReadinessCheck fail(String name, String msg, String fix, boolean critical) {
        return builder().name(name).status(LiveReadinessStatus.FAIL)
                .message(msg).fixHint(fix).critical(critical).build();
    }

    public static LiveReadinessCheck unknown(String name, String msg) {
        return builder().name(name).status(LiveReadinessStatus.UNKNOWN).message(msg).build();
    }
}
