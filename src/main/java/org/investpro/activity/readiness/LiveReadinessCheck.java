package org.investpro.activity.readiness;

import java.util.function.Supplier;

public record LiveReadinessCheck(String name, Supplier<LiveReadinessStatus> probe) {}
