package com.razorpay.featureflags;

import java.util.Objects;

public record TargetingRule(Condition condition, int priority, Object value) {
    public TargetingRule {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(value, "value");
    }
}
