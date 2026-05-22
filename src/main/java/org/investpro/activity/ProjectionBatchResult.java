package org.investpro.activity;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProjectionBatchResult {
    int attempted;
    int applied;
    int skipped;
    int failed;
    @Singular List<ProjectionResult> results;

    public boolean successful() {
        return failed == 0;
    }
}
