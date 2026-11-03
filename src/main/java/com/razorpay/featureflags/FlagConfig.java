package com.razorpay.featureflags;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record FlagConfig(String flagName, FlagType type, Object defaultValue,
                         List<TargetingRule> rules, RolloutConfig rollout) {
    public FlagConfig {
        if (flagName == null || flagName.isBlank()) {
            throw new IllegalArgumentException("Flag name must not be blank");
        }
        Objects.requireNonNull(type, "type");
        validateValue(type, defaultValue);
        rules = List.copyOf(rules).stream()
                .sorted(Comparator.comparingInt(TargetingRule::priority)).toList();
        for (var rule : rules) {
            validateValue(type, rule.value());
        }
        if (rollout != null) {
            validateValue(type, rollout.value());
        }
    }

    private static void validateValue(FlagType type, Object value) {
        if (!type.accepts(value)) {
            throw new IllegalArgumentException("Value does not match flag type " + type);
        }
    }
}
